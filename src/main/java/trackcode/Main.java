// Copyright Barry DeZonia 2021-2022
// All rights reserved

package trackcode;

import java.io.BufferedInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nom.bdezonia.zorbage.algebra.G;
import nom.bdezonia.zorbage.storage.ragged.RaggedStorageUnsignedInt8;
import nom.bdezonia.zorbage.tuple.Tuple2;
import nom.bdezonia.zorbage.type.geom.polygonalchain.PolygonalChainMember;

/**
 * 
 * @author Barry DeZonia
 *
 */
public class Main {

	// NOTE TRAKVIS code is the old way. Now using the MRTRIX3 code that follows much later.
	
	// TRAKVIS code =====================================================================

	public static void loadTrakData() {
	
		String filename = "/home/bdezonia/images/nifti/DTI_Lab3_tracts.trk";
		
		System.out.println("About to find stats");

		Tuple2<Long, Long> trakFileInfo = findStats(filename);

		System.out.println("Did find stats");

		long numElements = trakFileInfo.a();
		
		long numFloats = trakFileInfo.b();
		
		System.out.println("About to allocate 0 filled ragged data");

		RaggedStorageUnsignedInt8<PolygonalChainMember> raggedData =
				new RaggedStorageUnsignedInt8<>(numElements, (32 * numElements) + (4 * numFloats));
		
		System.out.println("Done allocating 0 filled ragged data");
		
		fillRaggedData(filename, raggedData);

		System.out.println("Done filling ragged data");
		
		if (numElements != raggedData.size())
			throw new IllegalArgumentException("mismatch: numElems counted "+numElements+"  ragged size "+raggedData.size());
		
		PolygonalChainMember chain = G.CHAIN.construct();
		
		for (long i = 0; i < numElements; i++) {
		
			raggedData.get(i, chain);
		}

		System.out.println("Done iterating ragged data");
	}

	public static Tuple2<Long, Long> findStats(String filename) {

		Tuple2<Long,Long> tup = new Tuple2<Long, Long>(null, null);
		
		boolean fileIsLittleEndian = false;
		
		try {
			
			fileIsLittleEndian = TrakUtils.fileIsLittleEndian(filename);
		
		} catch (IOException e) {
		
			System.err.println("EXITING: AN IO EXCEPTION OCCURRED: " + e.getMessage());

			System.exit(1);  // return error condition
		}
		
		File file = new File(filename);
		
		FileInputStream fileStream = null;
		
		BufferedInputStream bufStream = null;
		
		try {
		
			fileStream = new FileInputStream(file);
			
			bufStream = new BufferedInputStream(fileStream);
		
		} catch (FileNotFoundException e) {
		
			System.err.println("EXITING: FILE NOT FOUND: " + filename);
			
			System.exit(2);  // return error condition
		}
		
		DataInputStream dataStream = new DataInputStream(bufStream);

		TrakHeader header = new TrakHeader();
		
		try {
		
			header = TrakHeader.readFromSource(dataStream, fileIsLittleEndian);
			
			// report header vals
			
			// header.print(System.out);
			
			// and then read data if desired

			tup = TrakData.countEntities(dataStream, fileIsLittleEndian, header);
			
			dataStream.close();
			
		} catch (IOException e) {

			System.err.println("EXITING: COULD NOT READ FILE HEADER: " + e.getMessage());
			
			System.exit(3);  // return error condition
		}
		
		return tup;
	}

	public static void fillRaggedData(String filename, RaggedStorageUnsignedInt8<PolygonalChainMember> data) {
		
		boolean fileIsLittleEndian = false;
		
		try {
			
			fileIsLittleEndian = TrakUtils.fileIsLittleEndian(filename);
		
		} catch (IOException e) {
		
			System.err.println("EXITING: AN IO EXCEPTION OCCURRED: " + e.getMessage());

			System.exit(1);  // return error condition
		}
		
		File file = new File(filename);
		
		FileInputStream fileStream = null;
		
		BufferedInputStream bufStream = null;
		
		try {
		
			fileStream = new FileInputStream(file);
			
			bufStream = new BufferedInputStream(fileStream);
		
		} catch (FileNotFoundException e) {
		
			System.err.println("EXITING: FILE NOT FOUND: " + filename);
			
			System.exit(2);  // return error condition
		}
		
		DataInputStream dataStream = new DataInputStream(bufStream);

		TrakHeader header = new TrakHeader();
		
		try {
		
			header = TrakHeader.readFromSource(dataStream, fileIsLittleEndian);
			
			// report header vals
			
			// header.print(System.out);
			
			// and then read data if desired

			buildEntities(dataStream, fileIsLittleEndian, header, data);
			
			dataStream.close();
			
		} catch (IOException e) {

			System.err.println("EXITING: COULD NOT READ FILE HEADER: " + e.getMessage());
			
			System.exit(3);  // return error condition
		}
	}

	public static void buildEntities(DataInputStream source,
										boolean dataIsLittleEndian,
										TrakHeader header,
										RaggedStorageUnsignedInt8<PolygonalChainMember> chains)
		throws IOException
	{
		int numScalarsPerPoint = header.n_scalars + 3;
		
		int numPropertiesPerTrack = header.n_properties;
		
		long element = 0;

		while (true) {
			
			// read a track
			
			int numPointsInTrack = 0;

			try {
				
				numPointsInTrack = TrakUtils.readInt(source, dataIsLittleEndian);
				
			} catch (IOException e) {
				
				// assume we could not read next item since data source is at end
				
				// so return as successful
				
				return;
			}
			
			// read the track a point at a time

			float[] xs = new float[numPointsInTrack];
			float[] ys = new float[numPointsInTrack];
			float[] zs = new float[numPointsInTrack];
			
			for (int pt = 0; pt < numPointsInTrack; pt++) {
				
				// read all the scalars associated with one point in the track
				
				for (int sc = 0; sc < numScalarsPerPoint; sc++) {
				
					// note that in here:
					
					// sc == 0 : x coordinate of this one point in the track
					// sc == 1 : y coordinate of this one point in the track
					// sc == 2 : z coordinate of this one point in the track
					// sc == 3 : 1st scalar of this one point in the track
					// sc == 4 : 2nd scalar of this one point in the track
					// etc.   : etc.
					
					float scalar = TrakUtils.readFloat(source, dataIsLittleEndian);

					// YOUR JOB: do something with this scalar value
					
					if (sc == 0)
						xs[pt] = scalar;
					else if (sc == 1)
						ys[pt] = scalar;
					else if (sc == 2)
						zs[pt] = scalar;
					
				}
			}
			
			// read the properties associated with this one track
			
			for (int prop = 0; prop < numPropertiesPerTrack; prop++) {
				
				// note that in here:
				
				// prop == 0 : 1st property of whole track
				// prop == 1 : 2nd property of whole track
				// etc.      : etc.

				@SuppressWarnings("unused")
				float property = TrakUtils.readFloat(source, dataIsLittleEndian);

				// YOUR JOB: do something with this property value
			}
			
			// stream.println("Just read track "+tracksSoFar);

			PolygonalChainMember chain = new PolygonalChainMember(xs,ys,zs);
			
			chains.place(element, chain);

			element++;
		}
	}
	
	public static class TrakHeader {

		String id_string = ""; // 6 chars: 1st 5 == TRACK
		short xDim;
		short yDim;
		short zDim;
		float xScale;
		float yScale;
		float zScale;
		float xOrigin;  // TrackVis might not use origin. Others may.
		float yOrigin;
		float zOrigin;
		short n_scalars;
		String[] scalarNames = new String[] {"","","","","","","","","",""};  // 10 of at most 20 chars each
		short n_properties;
		String[] propertyNames = new String[] {"","","","","","","","","",""};  // 10 of at most 20 chars each
		float[][] vox_to_ras = new float[][] {new float[4],new float[4],new float[4],new float[4]};  // 4 x 4 : if [3][3] == 0 then matrix is to be ignored
		byte[] reserved = new byte[444];  // 444 values
		char[] axis_order = new char[4];  // 4 values: stored as 1 byte ascii chars
		byte[] pad2 = new byte[4];  // 4 values: paddings
		float[] image_orientation_patient = new float[6];  // 6 values: from DICOM header
		byte[] pad1 = new byte[2];  // 2 values: paddings
		byte invert_x;  // inversion flag: internal use only
		byte invert_y;  // inversion flag: internal use only
		byte invert_z;  // inversion flag: internal use only
		byte swap_xy;  // rotation flag: internal use only
		byte swap_yz;  // rotation flag: internal use only
		byte swap_zx;  // rotation flag: internal use only
		int n_count;  // number of tracks in file: if 0 then just read until they are exhausted
		int version;  // version number: I based my code on version 2 as defined by trackvis people
		int hdr_size;  // use to determine byte swapping: should == 1000
		
		public TrakHeader() { }

		public void print(PrintStream stream) {
			
			stream.println("id string: " + id_string);
			stream.println("hdr size: " + hdr_size + " (if == 1000 no byte swapping needed)");
			stream.println("hdr version: " + version);
			stream.println("x dim: " + xDim);
			stream.println("y dim: " + yDim);
			stream.println("z dim: " + zDim);
			stream.println("x scale: " + xScale);
			stream.println("y scale: " + yScale);
			stream.println("z scale: " + zScale);
			stream.println("x offset: " + xOrigin);
			stream.println("y offset: " + yOrigin);
			stream.println("z offset: " + zOrigin);
			stream.println("n_scalars: " + n_scalars);
			for (int i = 0; i < Math.min(n_scalars, 10); i++) {
				stream.println("  scalar " + i + " name: "+ scalarNames[i]);
			}
			stream.println("n_properties: " + n_properties);
			for (int i = 0; i < Math.min(n_properties, 10); i++) {
				stream.println("  property " + i + " name: "+ propertyNames[i]);
			}
			for (int i = 0; i < 4; i++) {
				for (int j = 0; j < 4; j++) {
					stream.println("matrix " + i + "," + j + ": " + vox_to_ras[i][j]);
				}
			}
			for (int i = 0; i < 4; i++) {
				stream.println("axis order "+i+": " + axis_order[i]);
			}
			for (int i = 0; i < 4; i++) {
				stream.println("pad2 "+i+": " + pad2[i]);
			}
			for (int i = 0; i < 6; i++) {
				stream.println("patient orientation " + i + ": " + image_orientation_patient[i]);
			}
			for (int i = 0; i < 2; i++) {
				stream.println("pad1 "+i+": " + pad1[i]);
			}
			stream.println("invert_x: " + invert_x);
			stream.println("invert_y: " + invert_y);
			stream.println("invert_z: " + invert_z);
			stream.println("swap_xy: " + swap_xy);
			stream.println("swap_yz: " + swap_yz);
			stream.println("swap_zx: " + swap_zx);
			stream.println("track count: " + n_count);
		}
		
		public static TrakHeader readFromSource(DataInput source, boolean dataIsLittleEndian) throws IOException {
			
			TrakHeader header = new TrakHeader();
			
			header.id_string = TrakUtils.readString(source, 6);
			header.xDim = TrakUtils.readShort(source, dataIsLittleEndian);
			header.yDim = TrakUtils.readShort(source, dataIsLittleEndian);
			header.zDim = TrakUtils.readShort(source, dataIsLittleEndian);
			header.xScale = TrakUtils.readFloat(source, dataIsLittleEndian);
			header.yScale = TrakUtils.readFloat(source, dataIsLittleEndian);
			header.zScale = TrakUtils.readFloat(source, dataIsLittleEndian);
			header.xOrigin = TrakUtils.readFloat(source, dataIsLittleEndian);
			header.yOrigin = TrakUtils.readFloat(source, dataIsLittleEndian);
			header.zOrigin = TrakUtils.readFloat(source, dataIsLittleEndian);
			header.n_scalars = TrakUtils.readShort(source, dataIsLittleEndian);
			for (int i = 0; i < 10; i++) {
				header.scalarNames[i] = TrakUtils.readString(source, 20);
			}
			header.n_properties = TrakUtils.readShort(source, dataIsLittleEndian);
			for (int i = 0; i < 10; i++) {
				header.propertyNames[i] = TrakUtils.readString(source, 20);
			}
			for (int i = 0; i < 4; i++) {
				for (int j = 0; j < 4; j++) {
					header.vox_to_ras[i][j] = TrakUtils.readFloat(source, dataIsLittleEndian);
				}			
			}
			for (int i = 0; i < 444; i++) {
				header.reserved[i] = TrakUtils.readByte(source);
			}
			for (int i = 0; i < 4; i++) {
				header.axis_order[i] = (char)TrakUtils.readByte(source);
			}
			for (int i = 0; i < 4; i++) {
				header.pad2[i] = TrakUtils.readByte(source);
			}
			for (int i = 0; i < 6; i++) {
				header.image_orientation_patient[i] = TrakUtils.readFloat(source, dataIsLittleEndian);
			}
			for (int i = 0; i < 2; i++) {
				header.pad1[i] = TrakUtils.readByte(source);
			}
			header.invert_x = TrakUtils.readByte(source);
			header.invert_y = TrakUtils.readByte(source);
			header.invert_z = TrakUtils.readByte(source);
			header.swap_xy = TrakUtils.readByte(source);
			header.swap_yz = TrakUtils.readByte(source);
			header.swap_zx = TrakUtils.readByte(source);

			header.n_count = TrakUtils.readInt(source, dataIsLittleEndian);
			header.version = TrakUtils.readInt(source, dataIsLittleEndian);
			header.hdr_size = TrakUtils.readInt(source, dataIsLittleEndian);
			
			return header;
		}
	}

	private static class TrakData {
	
		// This code shows anyone who wants to compile the input data into something a proper
		// way of going about it.
		
		public static Tuple2<Long,Long> countEntities(DataInput source, boolean dataIsLittleEndian, TrakHeader header) throws IOException {
			
			int numScalarsPerPoint = header.n_scalars + 3;
			
			int numPropertiesPerTrack = header.n_properties;
			
			long numElements = 0;
			
			long totalFloats = 0;
			
			while (true) {
				try {
			
					// read a track
					
					int numPointsInTrack = 0;
	
					try {
						
						numPointsInTrack = TrakUtils.readInt(source, dataIsLittleEndian);
						
					} catch (IOException e) {
						
						// assume we could not read next item since data source is at end
						
						// so return success
						
						return new Tuple2<Long, Long>(numElements, totalFloats);
					}
					
					// read the track a point at a time
					
					for (int pt = 0; pt < numPointsInTrack; pt++) {
						
						// read all the scalars associated with one point in the track
						
						for (int sc = 0; sc < numScalarsPerPoint; sc++) {
						
							// note that in here:
							
							// sc == 0 : x coordinate of this one point in the track
							// sc == 1 : y coordinate of this one point in the track
							// sc == 2 : z coordinate of this one point in the track
							// sc == 3 : 1st scalar of this one point in the track
							// sc == 4 : 2nd scalar of this one point in the track
							// etc.   : etc.
							
							@SuppressWarnings("unused")
							float scalar = TrakUtils.readFloat(source, dataIsLittleEndian);
	
							// YOUR JOB: do something with this scalar value
							
							if (sc < 3)
								totalFloats++;
						}
					}
					
					// read the properties associated with this one track
					
					for (int prop = 0; prop < numPropertiesPerTrack; prop++) {
						
						// note that in here:
						
						// prop == 0 : 1st property of whole track
						// prop == 1 : 2nd property of whole track
						// etc.      : etc.
	
						@SuppressWarnings("unused")
						float property = TrakUtils.readFloat(source, dataIsLittleEndian);
	
						// YOUR JOB: do something with this property value
						
						if (prop < 0)  // impossible: a purposely written blocker
							totalFloats++;
					}
					
					// stream.println("Just read track "+tracksSoFar);
					
					numElements++;
					
				} catch (Exception e) {
	
					// dies mid track : assume it is some weird error
					
					System.out.println("Unknown error: " + e.getMessage() + " Exiting.");
					
					return new Tuple2<Long, Long>(0L, 0L);
				}
			}
		}
	}
	
	private static class TrakUtils {
		
		public static boolean fileIsLittleEndian(String filename) throws IOException  {
			
			File file = new File(filename);
			
			FileInputStream fis = new FileInputStream(file);
			
			DataInputStream dis = new DataInputStream(fis);
			
			boolean retVal = dataIsLittleEndian(dis);
			
			dis.close();
			
			return retVal;
		}

		/**
		 * NOTE: this routine assumes that the data source is at offset 0 (the beginning of the "stream").
		 * 
		 * @param source
		 * @return
		 * @throws IOException
		 */
		public static boolean dataIsLittleEndian(DataInput source) throws IOException {
			
			source.skipBytes(996);
			
			int val = source.readInt();
			
			return (val != 1000);
		}

		public static String readString(DataInput source, int maxChars) throws IOException {
			
			String s = "";
			
			boolean done = false;
			
			for (int i = 0; i < maxChars; i++) {
			
				byte ch = source.readByte();
				
				if (!done) {
				
					if (ch == 0)
					
						done = true;
					
					else
					
						s += (char) ch;
				}
			}
			
			return s;
		}

		public static byte readByte(DataInput source) throws IOException {
			
			return source.readByte();
		}
		
		public static short readShort(DataInput source, boolean dataIsLittleEndian) throws IOException {

			if (dataIsLittleEndian) {
			
				int b0 = source.readByte() & 0xff;
				int b1 = source.readByte() & 0xff;
				
				short value = (short) ((b1 << 8) | (b0 << 0));
				
				return value;
			}
			else {
				
				return source.readShort();
			}
		}

		public static int readInt(DataInput source, boolean dataIsLittleEndian) throws IOException {
			
			if (dataIsLittleEndian) {
			
				int b0 = source.readByte() & 0xff;
				int b1 = source.readByte() & 0xff;
				int b2 = source.readByte() & 0xff;
				int b3 = source.readByte() & 0xff;
				
				int intBits = (b3 << 24) | (b2 << 16) | (b1 << 8) | (b0 << 0);
				
				return intBits;
			}
			else {
				
				return source.readInt();
			}
		}

		public static float readFloat(DataInput source, boolean dataIsLittleEndian) throws IOException {
			
			if (dataIsLittleEndian) {
			
				int b0 = source.readByte() & 0xff;
				int b1 = source.readByte() & 0xff;
				int b2 = source.readByte() & 0xff;
				int b3 = source.readByte() & 0xff;
				
				int intBits = (b3 << 24) | (b2 << 16) | (b1 << 8) | (b0 << 0);
				
				return Float.intBitsToFloat(intBits);
			}
			else {
				
				return source.readFloat();
			}
		}
	}

	// MRTRIX3 code =====================================================================
	
	private enum DataType{ 
		Unknown, 	// unknown data type
		Float32, 	// 32-bit floating-point (native endian-ness)
		Float32BE, 	// 32-bit floating-point (big-endian)
		Float32LE, 	// 32-bit floating-point (little-endian)
		Float64,	// 64-bit (double) floating-point (native endian-ness)
		Float64BE, 	// 64-bit (double) floating-point (big-endian)
		Float64LE; 	// 64-bit (double) floating-point (little-endian)
	}

	public static int numBytes(DataType type) {
		switch (type) {
			case Float32:
			case Float32BE:
			case Float32LE:
				return 4;
			case Float64:
			case Float64BE:
			case Float64LE:
				return 8;
			default:
				return 0;
		}
	}
	
	public static Tuple2<Long,Long> preprocessTckFile(String filename) {
		
		File file = new File(filename);
		
		FileInputStream fileStream = null;
		
		PushbackInputStream pbStream = null;
		
		try {
		
			fileStream = new FileInputStream(file);
			
			pbStream = new PushbackInputStream(fileStream);
		
		} catch (FileNotFoundException e) {
		
			System.err.println("EXITING: FILE NOT FOUND: " + filename);
			
			System.exit(1);  // return error condition
		}
		
		try {
			DataType dataType = readHeader(pbStream, fileStream);

			// position file point past possible garbage bytes due to data alignment issues
			
			long pos = fileStream.getChannel().position();
			long remaining = file.length() - pos;
			long cruft = remaining % (3 * numBytes(dataType));
			fileStream.getChannel().position(pos+cruft);
			
			BufferedInputStream bufStr = new BufferedInputStream(fileStream);
			
			DataInputStream dataStream = new DataInputStream(bufStr);
			
			long numTracks = 0;
			long numPoints = 0;
			
			float x = getBigEndianFloat(dataStream, dataType);
			float y = getBigEndianFloat(dataStream, dataType);
			float z = getBigEndianFloat(dataStream, dataType);
	
			while( ! (Float.isInfinite(x) && Float.isInfinite(y) && Float.isInfinite(z)) ) {
				
				long thisTrackSize = 0;
				
				while ( ! (Float.isNaN(x) && Float.isNaN(y) && Float.isNaN(z)) ) {
					
					thisTrackSize++;
					
					x = getBigEndianFloat(dataStream, dataType);
					y = getBigEndianFloat(dataStream, dataType);
					z = getBigEndianFloat(dataStream, dataType);
				}
				
				if (thisTrackSize > 0) {
					
					// make a track
		
					numPoints += thisTrackSize;
					numTracks++;
					
				}
				
				// finished a track
				
				x = getBigEndianFloat(dataStream, dataType);
				y = getBigEndianFloat(dataStream, dataType);
				z = getBigEndianFloat(dataStream, dataType);
				
			}
			
			// finished the file
		
			dataStream.close();
			
			BigDecimal avgTrackSize = BigDecimal.ZERO;
			
			if (numTracks > 0) {
				
				MathContext context = new MathContext(2);
			
				avgTrackSize = BigDecimal.valueOf(numPoints).divide(BigDecimal.valueOf(numTracks), context);
			}
			
			System.out.println("totalTracks    = " + numTracks);
			System.out.println("totalPoints    = " + numPoints);
			System.out.println("avg track size = " + avgTrackSize.doubleValue());
			
			return new Tuple2<Long,Long>(numTracks, numPoints);
		}
		catch (IOException e) {
			
			System.out.println("exception " + e);
		}
		return new Tuple2<Long,Long>(0L, 0L);
	}

	public static void readTckFile(String filename, RaggedStorageUnsignedInt8<PolygonalChainMember> data) {

		File file = new File(filename);
		
		FileInputStream fileStream = null;
		
		PushbackInputStream pbStream = null;
		
		try {
		
			fileStream = new FileInputStream(file);
			
			pbStream = new PushbackInputStream(fileStream);
		
		} catch (FileNotFoundException e) {
		
			System.err.println("EXITING: FILE NOT FOUND: " + filename);
			
			System.exit(1);  // return error condition
		}
		
		try {
			
			DataType dataType = readHeader(pbStream, fileStream);

			// position file point past possible garbage bytes due to data alignment issues
			
			long pos = fileStream.getChannel().position();
			long remaining = file.length() - pos;
			long cruft = remaining % (3 * numBytes(dataType));
			fileStream.getChannel().position(pos+cruft);
			
			BufferedInputStream bufStr = new BufferedInputStream(fileStream);
			
			DataInputStream dataStream = new DataInputStream(bufStr);
			
			float x = getBigEndianFloat(dataStream, dataType);
			float y = getBigEndianFloat(dataStream, dataType);
			float z = getBigEndianFloat(dataStream, dataType);
			
			long trackNumber = 0;
			
			while( ! (Float.isInfinite(x) && Float.isInfinite(y) && Float.isInfinite(z)) ) {
				
				ArrayList<Float> xs = new ArrayList<>();
				ArrayList<Float> ys = new ArrayList<>();
				ArrayList<Float> zs = new ArrayList<>();
			
				long thisTrackSize = 0;
				
				while ( ! (Float.isNaN(x) && Float.isNaN(y) && Float.isNaN(z)) ) {
					
					xs.add(x);
					ys.add(y);
					zs.add(z);
		
					thisTrackSize++;
					
					x = getBigEndianFloat(dataStream, dataType);
					y = getBigEndianFloat(dataStream, dataType);
					z = getBigEndianFloat(dataStream, dataType);
				}
				
				if (thisTrackSize > 0) {
					
					// make a track
					
					PolygonalChainMember chain = new PolygonalChainMember(xs, ys, zs);
					
					data.place(trackNumber, chain);
					
					trackNumber++;
				}
				
				// finished a track
				
				x = getBigEndianFloat(dataStream, dataType);
				y = getBigEndianFloat(dataStream, dataType);
				z = getBigEndianFloat(dataStream, dataType);
				
			}
			
			// finished the file
		
			dataStream.close();
			
		}
		catch (IOException e) {
			
			System.out.println("exception " + e);
		}
	}
	
	public static DataType readHeader(PushbackInputStream pbStream, FileInputStream fstream) throws IOException {
		
		// Do I need special line ending code? Or will java just handle?
		
		DataType dataType = DataType.Unknown;
		
		Pattern p = null; 
		
		boolean done = false;
		
		while (!done) {
			
			// is it end of input?
		
			String line = readLine(pbStream);
		
			line = line.trim();

			if (line.equalsIgnoreCase("end")) {
				done = true;
			}
			else {
		
				// is it dataType?
				
				p = Pattern.compile("(.+)\\:(.+)");
				Matcher m = p.matcher(line);
				
				if (m.matches()) {
					if (m.groupCount() == 2) {
						String key = m.group(1);
						if (key.trim().equalsIgnoreCase("datatype")) {
							String value = m.group(2).trim();
							
							if (value.equalsIgnoreCase("float32"))
								dataType = DataType.Float32;
							else if (value.equalsIgnoreCase("float32be"))
								dataType = DataType.Float32BE;
							else if (value.equalsIgnoreCase("float32le"))
								dataType = DataType.Float32LE;
							else if (value.equalsIgnoreCase("float64"))
								dataType = DataType.Float64;
							else if (value.equalsIgnoreCase("float64be"))
								dataType = DataType.Float64BE;
							else if (value.equalsIgnoreCase("float64le"))
								dataType = DataType.Float64LE;
							
							//System.out.println("data type set to "+dataType);
						}
					}
				}
			}
		}

		return dataType;
	}
	
	public static float getBigEndianFloat(DataInputStream data, DataType dataType) throws IOException {
		
		long b0, b1, b2, b3, b4, b5, b6, b7;
		
		switch (dataType) {
			
			case Float32:
			case Float32BE:
			
				return data.readFloat();
			
			case Float32LE:
			
				b0 = data.readByte() & 0xff;
				b1 = data.readByte() & 0xff;
				b2 = data.readByte() & 0xff;
				b3 = data.readByte() & 0xff;
	
				int intBits = (int) ((b3 << 24) | (b2 << 16) | (b1 << 8) | (b0 << 0));
				
				return Float.intBitsToFloat(intBits);
	
			case Float64:
			case Float64BE:
			
				return (float) data.readDouble();
			
			case Float64LE:

				b0 = data.readByte() & 0xff;
				b1 = data.readByte() & 0xff;
				b2 = data.readByte() & 0xff;
				b3 = data.readByte() & 0xff;
				b4 = data.readByte() & 0xff;
				b5 = data.readByte() & 0xff;
				b6 = data.readByte() & 0xff;
				b7 = data.readByte() & 0xff;
				
				long longBits = (b7 << 56) | (b6 << 48) | (b5 << 40) | (b4 << 32) | (b3 << 24) | (b2 << 16) | (b1 << 8) | (b0 << 0);
				
				return (float) Double.longBitsToDouble(longBits);
	
			
			default:
				return Float.POSITIVE_INFINITY;
		}
	}

	public static String readLine(PushbackInputStream pbStream) throws IOException {

		StringBuilder sb = new StringBuilder();
		boolean done = false;
		while (!done) {
			int b = pbStream.read();
			if (b == 0x0A) {  // line feed by self
				done = true;
			}
			else if (b == 0x0D) {  // carriage return by self
				int b2 = pbStream.read();
				if (b2 != 0x0A) {  // or also followed by a line feed
					pbStream.unread(b2);
				}
				done = true;
			}
			else {
				sb.append((char) b);
			}
		}
		return sb.toString();
	}

	private static void updateBounds(RaggedStorageUnsignedInt8<PolygonalChainMember> raggedData) {
		
		PolygonalChainMember polyChain = G.CHAIN.construct();
		
		for (long i = 0; i < raggedData.size(); i++) {
			raggedData.get(i, polyChain);
			polyChain.getMinX();
			raggedData.set(i, polyChain);
		}
	}
	
	private static long searchTracts(RaggedStorageUnsignedInt8<PolygonalChainMember> raggedData) {
		
		long found = 0;
		
		PolygonalChainMember polyChain = G.CHAIN.construct();
		
		for (long i = 0; i < raggedData.size(); i++) {
			raggedData.get(i, polyChain);
			if (G.CHAIN.intersect().call(25f, 25f, 25f, 26f, 26f, 26f, polyChain))
				found++;
			polyChain.getMinX();
			raggedData.set(i, polyChain);
		}
		
		return found;
	}
	
	public static void main(String[] args) {

		//long lStart = System.currentTimeMillis();

		//loadTrakData();
		
		//long lEnd = System.currentTimeMillis();
		
		//System.out.println("Total load time at least " + ((lEnd - lStart)/1000) + " seconds");
	

		String fname = "/home/bdezonia/waisman/set2/tractography_01M.tck";  // reads in 4.8 secs, loads in 9 secs, calcs/stores bounds in 3.3 secs, searches in 21 secs, finds 306 tracts
//		String fname = "/home/bdezonia/waisman/set2/tractography_20M.tck";  // reads in 97 secs, loads in 580 secs, calcs/stores bounds in 830 secs, searches in 1170 secs, finds 6807 tracts
//		String fname = "/home/bdezonia/waisman/set2/tractography.tck";      // reads in 1000 secs, loads in 6030 secs, calcs/stores bounds in 8944 secs, searches in 12000 secs, finds 59417 tracts
	
		System.out.println("Start reading");

		long a = System.currentTimeMillis();
		
		Tuple2<Long,Long> result = preprocessTckFile(fname);
		
		long b = System.currentTimeMillis();
		
		System.out.println("Done in "+((b-a)/1000.0)+" secs");
		
		long numTracks = result.a();
		
		long numPoints = result.b();
		
		System.out.println("Start loading");
		
		RaggedStorageUnsignedInt8<PolygonalChainMember> raggedData =
				new RaggedStorageUnsignedInt8<>(numTracks, ((1 + 7) * 4 * numTracks) + (3 * 4 * numPoints));
		
		readTckFile(fname, raggedData);
		
		long c = System.currentTimeMillis();
		
		System.out.println("Done loading data after another "+((c-b)/1000.0)+" secs");
		
		updateBounds(raggedData);
		
		long d = System.currentTimeMillis();

		System.out.println("Bounds calcing/storing took "+((d-c)/1000.0)+" secs after that");
		
		long numFound = searchTracts(raggedData);

		long e = System.currentTimeMillis();

		System.out.println("Searching took "+((e-d)/1000.0)+" secs after that");
		
		System.out.println("Num found = "+numFound);
	}
	
}
