/**
 * S3 storage client binding for YCSB.
 *
 * Submitted by Ivan Baldinotti on 14/07/2015
 *
 */
package com.yahoo.ycsb.db;

import java.util.HashMap;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.io.IOException;

import com.yahoo.ycsb.ByteArrayByteIterator;
import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.DBException;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.*;
import com.amazonaws.auth.*;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;

/**
 * S3 Storage client for YCSB framework.
 * 
 * Properties to set:
 * 
 * s3.accessKeyId=access key S3 aws
 * s3.secretKey=secret key S3 aws
 * s3.endPoint=s3.amazonaws.com
 * s3.region=us-east-1
 *
 * @author ivanB1975
 */
public class S3Client extends DB {
 
	private static String key;
    	private static String bucket;
	private static String accessKeyId;
	private static String secretKey;
	private static String endPoint;
	private static String region;
	private static String maxErrorRetry;
	private static BasicAWSCredentials s3Credentials;
	private static AmazonS3Client s3Client;
	private static ClientConfiguration clientConfig;

	/**
	* Cleanup any state for this storage.
	* Called once per S3 instance; there is one S3 instance per client thread.
	*/
	@Override
	public void cleanup() throws DBException {
		try {
		//this.s3Client.shutdown(); //this should not be used
		//this.s3Client = null;
		} catch (Exception e){
			e.printStackTrace();
		}
    	}
	/**
	* Delete a file from S3 Storage.
	* 
	* @param bucket
	*            The name of the bucket
	* @param key
	*            The record key of the file to delete.
	* @return Zero on success, a non-zero error code on error. See the
	*         {@link DB} class's description for a discussion of error codes.
	*/
	@Override
    	public int delete(String bucket, String key) {
		try {
			this.s3Client.deleteObject(new DeleteObjectRequest(bucket, key));
		} catch (Exception e){
			e.printStackTrace();
			return 1;
		}
     		return 0;
    	}
	/**
	* Initialize any state for the storage.
	* Called once per S3 instance; If the client is not null it is re-used.
	*/
	@Override
	public void init() throws DBException {
       		synchronized (S3Client.class){
			Properties props = getProperties();
			accessKeyId = props.getProperty("s3.accessKeyId","accessKeyId");
			secretKey = props.getProperty("s3.secretKey","secretKey");
			endPoint = props.getProperty("s3.endPoint","s3.amazonaws.com");
			region = props.getProperty("s3.region","us-east-1");
			maxErrorRetry = props.getProperty("s3.maxErrorRetry","15");
			System.out.println("Inizializing the S3 connection");
			s3Credentials = new BasicAWSCredentials(accessKeyId,secretKey);
			clientConfig = new ClientConfiguration();
			clientConfig.setMaxErrorRetry(Integer.parseInt(maxErrorRetry));
			if (s3Client != null) {
				System.out.println("Reusing the same client");
				return;
			} 
			try {
				s3Client = new AmazonS3Client(s3Credentials,clientConfig);
				s3Client.setRegion(Region.getRegion(Regions.fromName(region)));
				s3Client.setEndpoint(endPoint);
				System.out.println("Connection successfully initialized");
			} catch (Exception e){
				System.err.println("Could not connect to S3 storage because: "+ e.toString());
				e.printStackTrace();
				return;
			}
		}
    	}
	/**
	* Create a new File in the Bucket. Any field/value pairs in the specified
	* values HashMap will be written into the file with the specified record
	* key.
	* 
	* @param bucket
	*            The name of the bucket
	* @param key
	*            The record key of the file to insert.
	* @param values
	*            A HashMap of field/value pairs to insert in the file
	* @return Zero on success, a non-zero error code on error. See the
	*         {@link DB} class's description for a discussion of error codes.
	*/    
	@Override
	public int insert(String bucket, String key, HashMap<String, ByteIterator> values) {
		return writeToStorage(bucket,key,values,0);
	}
  	
	/**
	* Read a file from the Bucket. Each field/value pair from the result
	* will be stored in a HashMap.
	* 
	* @param bucket
     	*            The name of the bucket
	* @param key
	*            The record key of the file to read.
	* @param fields
	*            The list of fields to read, or null for all of them, it is null by default
	* @param result
	*            A HashMap of field/value pairs for the result
	* @return Zero on success, a non-zero error code on error or "not found".
	*/
	@Override
	public int read(String bucket, String key, Set<String> fields,HashMap<String, ByteIterator> result) {
		return readFromStorage(bucket,key,result);	
	}
	/**
	* Update a file in the database. Any field/value pairs in the specified
	* values HashMap will be written into the file with the specified file
	* key, overwriting any existing values with the same field name.
	* 
	* @param bucket
	*            The name of the bucket
	* @param key
	*            The file key of the file to write.
	* @param values
	*            A HashMap of field/value pairs to update in the record
	* @return Zero on success, a non-zero error code on error. See this class's
	*         description for a discussion of error codes.
	*/
	@Override
	public int update(String bucket, String key,HashMap<String, ByteIterator> values) {
		return writeToStorage(bucket,key,values,1);
	}
	/**
	* Perform a range scan for a set of files in the bucket. Each
	* field/value pair from the result will be stored in a HashMap.
	* 
	* @param bucket
	*            The name of the bucket
	* @param startkey
	*            The file key of the first file to read.
	* @param recordcount
	*            The number of files to read
	* @param fields
	*            The list of fields to read, or null for all of them
	* @param result
	*            A Vector of HashMaps, where each HashMap is a set field/value
	*            pairs for one file
	* @return Zero on success, a non-zero error code on error. See the
	*         {@link DB} class's description for a discussion of error codes.
	*/
	@Override
	public int scan(String bucket, String startkey, int recordcount, Set<String> fields, Vector<HashMap<String, ByteIterator>> result) {
		return scanFromStorage(bucket,startkey,recordcount,result);
	}

	protected int writeToStorage(String bucket,String key,HashMap<String, ByteIterator> values, int updateMarker) {
		int totalSize = 0;
		int fieldCount = values.size(); //number of fields to concatenate
		Object keyToSearch = values.keySet().toArray()[0]; // getting the first field in the values
		byte[] sourceArray = values.get(keyToSearch).toArray(); // getting the content of just one field
		int sizeArray = sourceArray.length; //size of each array
		if (updateMarker == 0){
			totalSize = sizeArray*fieldCount;
		} else {
			try {
				S3Object object = this.s3Client.getObject(new GetObjectRequest(bucket, key));
				ObjectMetadata objectMetadata = this.s3Client.getObjectMetadata(bucket, key);
				int sizeOfFile = (int)objectMetadata.getContentLength();
				fieldCount = sizeOfFile/sizeArray;
				totalSize = sizeOfFile;
			} catch (Exception e){
				e.printStackTrace();
            			return 1;
			}
		}
		
		byte[] destinationArray = new byte[totalSize];
		int offset = 0;
    		for (int i = 0; i < fieldCount; i++) {
        		System.arraycopy(sourceArray, 0, destinationArray, offset, sizeArray);
        		offset += sizeArray;
    		}
		InputStream input = new ByteArrayInputStream(destinationArray);
		ObjectMetadata metadata = new ObjectMetadata();
		metadata.setContentLength(totalSize);
		try {
			PutObjectResult res = this.s3Client.putObject(bucket,key,input,metadata);
        	} catch (Exception e) {
            		e.printStackTrace();
            		return 1;
        	} finally {
	    		try {
        			input.close();
    			} catch (Exception e) {
        			e.printStackTrace();
            			return 1;
    			}
		return 0;
        	}
	}


	protected int readFromStorage(String bucket,String key, HashMap<String, ByteIterator> result) {
		try {
			S3Object object = this.s3Client.getObject(new GetObjectRequest(bucket, key));
			ObjectMetadata objectMetadata = this.s3Client.getObjectMetadata(bucket, key);
			InputStream objectData = object.getObjectContent(); // consuming the stream
			// writing the stream to bytes and to results
			int sizeOfFile = (int)objectMetadata.getContentLength();
			byte[] inputStreamToByte = new byte[sizeOfFile];
    			objectData.read(inputStreamToByte,0,sizeOfFile); // reading the stream to bytes
			result.put(key,new ByteArrayByteIterator(inputStreamToByte));
			objectData.close();
		} catch (Exception e){
			e.printStackTrace();
            		return 1;
		} finally {
            		return 0;
        	}
   	}

	protected int scanFromStorage(String bucket,String startkey, int recordcount, Vector<HashMap<String, ByteIterator>> result) {

		int counter = 0;	
		ObjectListing listing = s3Client.listObjects(bucket);
		List<S3ObjectSummary> summaries = listing.getObjectSummaries();
		List<String> keyList = new ArrayList();
		int startkeyNumber = 0;
		int numberOfIteration = 0;
		// getting the list of files in the bucket
		while (listing.isTruncated()) {
	   		listing = s3Client.listNextBatchOfObjects(listing);
	   		summaries.addAll (listing.getObjectSummaries());
		}
		for (S3ObjectSummary summary : summaries) {
		    String summaryKey = summary.getKey();               
		    keyList.add(summaryKey);
		}
		// Sorting the list of files in Alphabetical order
		Collections.sort(keyList); // sorting the list
		// Getting the position of the startingfile for the scan
		for (String key : keyList) {
			if (key.equals(startkey)){
				startkeyNumber = counter;
		    	} else {
				counter = counter + 1;
		    	}
		}
		// Checking if the total number of file is bigger than the file to read, if not using the total number of Files
		if (recordcount < keyList.size()) {
			numberOfIteration = recordcount;
		} else {
			numberOfIteration = keyList.size();
		}
		// Reading the Files starting from the startkey File till the end of the Files or Till the recordcount number
		for (int i = startkeyNumber; i < numberOfIteration; i++){
			HashMap<String, ByteIterator> resultTemp = new HashMap<String, ByteIterator>();
			readFromStorage(bucket,keyList.get(i),resultTemp);
			result.add(resultTemp);	
		}
		return 0;
	}
}
