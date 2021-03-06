package com.santi.jaime.TA;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.commons.io.FilenameUtils;
import org.imgscalr.*;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

public class ServerApp {

	private static final String				logPath		= "/var/log/success-image-server/";
	private static final String				bucketName	= "g3-bucket-2";
	private static final SimpleDateFormat	date		= new SimpleDateFormat("yyyy-MM-dd");
	private static final SimpleDateFormat	time		= new SimpleDateFormat("HH:mm:ss");

	public static void main(String[] args) throws Exception {

		AWSCredentials credentials = null;
		String logFileName = logPath + date.format(new Date()) + "-success.log";
		File logFile = new File("logs/" + logFileName);
		PrintWriter lfpw = new PrintWriter(logFile);

		try {
			credentials = new ProfileCredentialsProvider("default").getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException(e.getMessage());
		}

		AmazonSQS sqs = new AmazonSQSClient(credentials);
		Region euWest1 = Region.getRegion(Regions.EU_WEST_1);
		sqs.setRegion(euWest1);
		AmazonS3 s3client = new AmazonS3Client(new ProfileCredentialsProvider());

		// We attemp to receive the request, first of all we check if the outbox queue is already created
		boolean existsIn = false, existsOut = false;
		String sqsInbox = "";
		String sqsOutbox = "";
		for (String queueUrl : sqs.listQueues().getQueueUrls()) {
			String[] pieces = queueUrl.split("/");
			if (pieces[pieces.length - 1].equals("g3-inbox")) {
				existsIn = true;
				sqsInbox = queueUrl;
				lfpw.println(time.format(new Date()) + " - Inbox queue fetched");
				lfpw.flush();
			} else if (pieces[pieces.length - 1].equals("g3-outbox")) {
				existsOut = true;
				sqsOutbox = queueUrl;
				lfpw.println(time.format(new Date()) + " - Outbox queue fetched");
				lfpw.flush();
			}
		}
		if (!existsIn) {
			// It doesn't exist so we create it
			CreateQueueRequest createQueueRequest = new CreateQueueRequest("g3-inbox");
			sqsInbox = sqs.createQueue(createQueueRequest).getQueueUrl();
			lfpw.println(time.format(new Date()) + " - Inbox queue created.");
			lfpw.flush();
		}
		if (!existsOut) {
			// It doesn't exist so we create it
			CreateQueueRequest createQueueRequest = new CreateQueueRequest("g3-outbox");
			sqsInbox = sqs.createQueue(createQueueRequest).getQueueUrl();
			lfpw.println(time.format(new Date()) + " - Outbox queue created.");
			lfpw.flush();
		}

		// If the bucket doesn't exist we create it here
		if (!s3client.doesBucketExist(bucketName)) {
			s3client.createBucket(bucketName, com.amazonaws.services.s3.model.Region.EU_Ireland);
		}

		lfpw.println(time.format(new Date()) + " - Server startup OK.");
		lfpw.flush();

		// Now begins the working cycle
		while (true) {
			ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(sqsInbox).withMessageAttributeNames("sessionID").withMessageAttributeNames("action");
			int message_number = 0;
			List<Message> messages = null;
			do {
				messages = sqs.receiveMessage(receiveMessageRequest).getMessages();
				message_number = messages.size();
			} while (message_number == 0);

			// As soon as we get the message we delete it so that the other queue doesn't get it
			String messageReceiptHandle = messages.get(0).getReceiptHandle();
			sqs.deleteMessage(new DeleteMessageRequest(sqsInbox, messageReceiptHandle));

			MessageAttributeValue sessionID = messages.get(0).getMessageAttributes().get("sessionID");
			MessageAttributeValue action = messages.get(0).getMessageAttributes().get("action");
			String actionValue = action.getStringValue();

			lfpw.println(time.format(new Date()) + " - Received message from " + sessionID.getStringValue() + ".");
			lfpw.flush();

			String fileKey = messages.get(0).getBody();
			S3Object file = s3client.getObject(bucketName, fileKey);
			S3ObjectInputStream fileContent = file.getObjectContent();
			BufferedImage image = ImageIO.read(fileContent);
			BufferedImage imageTreated = null;

			// Performing the transformation...
			switch (actionValue) {
				case "brighter":
					imageTreated = Scalr.apply(image, Scalr.OP_BRIGHTER);
					break;
				case "darker":
					imageTreated = Scalr.apply(image, Scalr.OP_DARKER);
					break;
				case "black_white":
					imageTreated = Scalr.apply(image, Scalr.OP_GRAYSCALE);
					break;
			}

			String fileOutputName = new Date().getTime() + "_treated" + fileKey;
			File imageOutputFile = new File(fileOutputName);
			String extension = FilenameUtils.getExtension(fileOutputName);
			ImageIO.write(imageTreated, extension, imageOutputFile);

			s3client.putObject(bucketName, fileOutputName, imageOutputFile);

			sqs.sendMessage(new SendMessageRequest(sqsOutbox, fileOutputName).addMessageAttributesEntry("sessionID", sessionID));

			lfpw.println(time.format(new Date()) + " - " + fileKey + " transformed and uploaded into bucket.");
			lfpw.flush();
		}

	}

}
