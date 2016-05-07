package edu.wisc.cs.sdn.simpledns;

import java.net.*;
import java.io.*;
import edu.wisc.cs.sdn.simpledns.packet.*;
import java.util.*;

public class SimpleDNS {

	private final static int PACKETSIZE = 4096;
	private final static int listenPortDNS = 8053;
	private static InetAddress rootServer;
	private static boolean isFound = false;
	private static short dnsType;	
	private static DatagramSocket dnsReplySocket;
	private static DatagramPacket firstPacket, finalReply;
	private static InetAddress ownAddress;
	private static List<CSV> csvInput;


	private static void setDNSRequestFlags(DNS dnsRequestPacket)
	{
		dnsRequestPacket.setQuery(true);
		dnsRequestPacket.setOpcode((byte)0);
		dnsRequestPacket.setTruncated(false);
		dnsRequestPacket.setRecursionDesired(true);
		dnsRequestPacket.setAuthenicated(false);
	}

	private static void setDNSReplyFlags(DNS dnsReplyPacket)
	{
		dnsReplyPacket.setQuery(false);
		dnsReplyPacket.setOpcode((byte)0);
		dnsReplyPacket.setAuthoritative(false);
		dnsReplyPacket.setTruncated(false);
		dnsReplyPacket.setRecursionAvailable(true);
		dnsReplyPacket.setRecursionDesired(true);
		dnsReplyPacket.setAuthenicated(false);
		dnsReplyPacket.setCheckingDisabled(false);
		dnsReplyPacket.setRcode((byte)0);
	}

	private static void addTXTRecord(List<DNSResourceRecord> answerList) throws Exception
	{

		int i;

		DNSRdataAddress resolvedIP;
		String matchLocation = null;
		int mask = -1 ;


		for(i=0; i<answerList.size(); i++)
		{

			if(answerList.get(i).getType() != DNS.TYPE_A)	
			{
				continue;
			}
			resolvedIP = (DNSRdataAddress)answerList.get(i).getData();

			int j = 0;

			for (CSV curr : csvInput)
			{
				if (curr.ifMatch(resolvedIP.toString()))
				{
					if (curr.getMask() > mask)
					{
						mask = curr.getMask(); 
						matchLocation = curr.getLocation();
						System.out.println(matchLocation); 
					}                                     
				}                             
			}
			if(mask >= 0) //add entry
			{
				DNSResourceRecord dnsrr = new DNSResourceRecord();

				dnsrr.setName(answerList.get(i).getName());
				dnsrr.setTtl(60);
				dnsrr.setType(DNS.TYPE_TXT);
				System.out.println(matchLocation + "-" +resolvedIP.toString());
				DNSRdataString dnsrdatatext= new DNSRdataString(matchLocation + "-" +resolvedIP.toString());
				dnsrr.setData(dnsrdatatext);
				answerList.add(dnsrr);
			}
		}
	}

	/*	public static DatagramPacket parseRequestRecurse(DatagramPacket queryPacket,DNS dnsRequest)
		{
		List<DNSResourceRecord> authorities = null;
		List<DNSResourceRecord> dnsAdditionals = null;
		List<DNSResourceRecord> answers = null;
		DatagramPacket finalPacket = null;
	//void null = null;

	if(isFound == true)
	return finalPacket;

	System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());

	try
	{
	//int port = 53;
	System.out.println(nameServer);
	System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());

	//DatagramSocket socket = new DatagramSocket(port, InetAddress.getByName(nameServer));
	byte[] sendbuf = new byte[1500];
	DatagramPacket packet = new DatagramPacket(sendbuf,PACKETSIZE, InetAddress.getByName(nameServer), 53);
	//			packet.setAddress(InetAddress.getByName(nameServer));
	packet.setData(dnsRequest.serialize());
	socket.send(packet);

	//receive reply from nameServer = rootServer in first iteration 

	while(isFound != true)
	{
	DatagramPacket replyPacket = new DatagramPacket( new byte[PACKETSIZE], PACKETSIZE );
	socket.receive(replyPacket);


	//parse response
	DNS dnsResponse = DNS.deserialize(replyPacket.getData(), replyPacket.getLength());
	System.out.println("QUERY RES: " + dnsResponse);

	System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());

	answers = dnsResponse.getAnswers();
	authorities = dnsResponse.getAuthorities();
	dnsAdditionals = dnsResponse.getAdditional();
	List<DNSResourceRecord> aRecords = new LinkedList<DNSResourceRecord>();
	List<DNSResourceRecord> aaaaRecords = new LinkedList<DNSResourceRecord>();
	List<DNSResourceRecord> nsRecords = new LinkedList<DNSResourceRecord>();
	DNSResourceRecord cName = null;

	if( answers.size() >= 1 )
	{
	System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
	boolean createResponse;

	for(DNSResourceRecord current : answers)
	{
	short answerType = current.getType();
	switch(answerType)
	{
	case DNS.TYPE_A:  aRecords.add(current); break;
	case DNS.TYPE_AAAA: aaaaRecords.add(current); break;
	case DNS.TYPE_NS: nsRecords.add(current); break;
	case DNS.TYPE_CNAME: cName = current; break;
	};
	}

	createResponse = (dnsType == DNS.TYPE_A  && aRecords.size() >= 1) || (dnsType == DNS.TYPE_AAAA  && aaaaRecords.size() >= 1)
	|| (dnsType == DNS.TYPE_NS && nsRecords.size() >= 1) || (dnsType == DNS.TYPE_CNAME && cName != null);

	if(createResponse)
	{
	System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
	// create a new response, properly add the answers section (usually includes CNAME for no reason.)
	dnsResponse.setAnswers( new LinkedList<DNSResourceRecord>() );
	dnsResponse.setAuthorities( new LinkedList<DNSResourceRecord>() );

	// add answers
	if(dnsType == DNS.TYPE_A)
	{
		dnsResponse.setAnswers(aRecords);
	} else if (dnsType == DNS.TYPE_AAAA)
	{
		dnsResponse.setAnswers(aaaaRecords);
	} else if (dnsType == DNS.TYPE_NS)
	{
		dnsResponse.setAnswers(nsRecords);
	}       
	if (cName != null){
		dnsResponse.addAnswer(cName);
	}       

	byte[] receivehere = new byte[1500];
	byte[] payload = dnsResponse.serialize();
	finalPacket = new DatagramPacket(receivehere, PACKETSIZE, InetAddress.getByName("127.0.0.1"), 8053);
	finalPacket.setData(payload);

	System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
	DNS ans = DNS.deserialize(finalPacket.getData(), finalPacket.getData().length);
	isFound = true;
	break;			
	//		return finalPacket; 	

	//		socket.send(finalPacket);	
	//	return;

} else 
{
	System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
	// need to recurse on a given CNAME
	dnsRequest.getQuestions().get(0).setName(cName.getName());
	parseRequestRecurse(dnsRequest, nameServer);
}
}
else if(authorities.size() >= 1)
{
	System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
	String new_nameserver = null;

	for(DNSResourceRecord current : authorities)
	{
		for(DNSResourceRecord cur : dnsAdditionals)
		{
			if (current.getData().toString().equals(cur.getName().toString()))
			{
				if(cur.getType() == DNS.TYPE_A)
				{
					new_nameserver = cur.getData().toString();
					break;
				}
			}
		}
	}
	if(new_nameserver != null)
	{
		System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
		parseRequestRecurse(dnsRequest, new_nameserver);
	}
	else
	{
		System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
		new_nameserver = findIPAddr(authorities.get(0).getName(), rootServer);
		parseRequestRecurse(dnsRequest, socket, new_nameserver);
	}
	//	return null;
}
}
}
catch(Exception e)
{
	e.printStackTrace();
}
return finalPacket;
}
*/

private static DatagramPacket parseRequestRecurse(DatagramPacket queryPacket,DNS dnsRequestPacket) throws Exception
{

	System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
	DatagramPacket newQueryPacket = null;
	DatagramPacket receivePacket = null;
	DatagramPacket finalReplyPacket = null;

	DNS dnsQueryPacket,dnsReceivePacket;
	List<DNSResourceRecord> cnameList = new ArrayList<DNSResourceRecord>();
	List<DNSResourceRecord> lastAuthList = new ArrayList<DNSResourceRecord>();
	List<DNSResourceRecord> lastAddList = new ArrayList<DNSResourceRecord>();

	boolean resolved = false;
	byte[] receiveData = new byte[4096];

	newQueryPacket = new DatagramPacket(queryPacket.getData(),queryPacket.getLength(),rootServer,53);
	receivePacket = new DatagramPacket(receiveData,receiveData.length);

	dnsReplySocket.send(newQueryPacket);

	while(!resolved){

		dnsReplySocket.receive(receivePacket);

		dnsReceivePacket = DNS.deserialize(receivePacket.getData(),receivePacket.getData().length);
		List<DNSResourceRecord> authorityList = dnsReceivePacket.getAuthorities();
		List<DNSResourceRecord> additionalList = dnsReceivePacket.getAdditional();

		if(authorityList.size()>0){
			boolean listContainsNonSOA = false;
			for(int i=0; i<authorityList.size(); i++){
				if(authorityList.get(i).getType() == DNS.TYPE_A){
					listContainsNonSOA = true;
					break;
				}
				else if(authorityList.get(i).getType() == DNS.TYPE_AAAA){
					listContainsNonSOA = true;
					break;
				}
				else if(authorityList.get(i).getType() == DNS.TYPE_NS){
					listContainsNonSOA = true;
					break;
				}
				else if(authorityList.get(i).getType() == DNS.TYPE_CNAME){
					listContainsNonSOA = true;
					break;
				}
				else{

				}
			}
			if(listContainsNonSOA)
				lastAuthList = authorityList;
		}
		if(additionalList.size()>0){
			lastAddList = additionalList;
		}

		if(dnsReceivePacket.getAnswers().size()==0){

			InetAddress nextNSAddress = null;

			DNSResourceRecord authRR = null, addRR = null;
			boolean matchFound = false;

			for(int i=0; i<authorityList.size(); i++){

				authRR = authorityList.get(i);

				for(int j=0; j<additionalList.size(); j++){
					addRR = additionalList.get(j);
					DNSRdataName nsName = (DNSRdataName)authRR.getData();


					if(authRR.getType() == DNS.TYPE_NS && (addRR.getName().equals(nsName.getName()))
							&& (addRR.getType() == DNS.TYPE_A /*|| addRR.getType() == DNS.TYPE_AAAA*/)){
						matchFound = true;
						break;
					}
				}

				if(matchFound)
					break;
			}

			if(matchFound){
				System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
				DNSRdataAddress dnsRdata = (DNSRdataAddress)addRR.getData();
				nextNSAddress = dnsRdata.getAddress();
			}
			else{

				DNS finalDNSPacket = new DNS();

				List<DNSResourceRecord> finalAnswerList = dnsReceivePacket.getAnswers();
				List<DNSResourceRecord> finalAuthList = dnsReceivePacket.getAuthorities();
				List<DNSResourceRecord> finalAddList = dnsReceivePacket.getAdditional();

				for(int i=0; i<cnameList.size(); i++){
					finalAnswerList.add(0, cnameList.get(i));
				}

				finalDNSPacket.setQuestions(dnsRequestPacket.getQuestions());
				finalDNSPacket.setAnswers(finalAnswerList);

				for(int i=0; i<finalAuthList.size(); i++){
					switch(finalAuthList.get(i).getType()){
						case DNS.TYPE_A:
							break;
						case DNS.TYPE_AAAA:
							break;
						case DNS.TYPE_NS:
							break;
						case DNS.TYPE_CNAME:
							break;
						default:
							finalAuthList.remove(i);
							break;
					}
				}

				for(int i=0; i<finalAddList.size(); i++){
					switch(finalAddList.get(i).getType()){
						case DNS.TYPE_A:
							break;
						case DNS.TYPE_AAAA:
							break;
						case DNS.TYPE_NS:
							break;
						case DNS.TYPE_CNAME:
							break;
						default:
							finalAddList.remove(i);
							break;
					}
				}

				if(finalAuthList.size() == 0){
					finalAuthList = lastAuthList;
				}
				if(finalAddList.size() == 0){
					finalAddList = lastAddList;
				}

				finalDNSPacket.setAuthorities(finalAuthList);
				finalDNSPacket.setAdditional(finalAddList);

				setDNSReplyFlags(finalDNSPacket);
				finalDNSPacket.setId(dnsRequestPacket.getId());

				finalReplyPacket = new DatagramPacket(finalDNSPacket.serialize(),finalDNSPacket.getLength());

				return finalReplyPacket;
			}

			newQueryPacket = new DatagramPacket(newQueryPacket.getData(),newQueryPacket.getLength(),nextNSAddress,53);
			dnsReplySocket.send(newQueryPacket);
		}
		else{
			System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
			List<DNSResourceRecord> answerList = dnsReceivePacket.getAnswers();
			DNSResourceRecord answer = null;
			answer = answerList.get(0);

			if(answer.getType() == DNS.TYPE_CNAME){

				cnameList.add(answer);

				DNSQuestion newQuestion = new DNSQuestion();
				DNSRdataName dnsRdataname = (DNSRdataName)answer.getData();
				newQuestion.setName(dnsRdataname.getName());
				newQuestion.setType(dnsReceivePacket.getQuestions().get(0).getType());
				dnsQueryPacket = new DNS();

				setDNSRequestFlags(dnsQueryPacket);

				dnsQueryPacket.setId(dnsRequestPacket.getId());

				List<DNSQuestion> newQuestionList = new ArrayList<DNSQuestion>();
				newQuestionList.add(newQuestion);
				dnsQueryPacket.setQuestions(newQuestionList);

				newQueryPacket = new DatagramPacket(dnsQueryPacket.serialize(),dnsQueryPacket.getLength(),rootServer,53);
				dnsReplySocket.send(newQueryPacket);

			}
			else{
				System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
				resolved = true;

				List<DNSResourceRecord> finalAnswerList = dnsReceivePacket.getAnswers();

				// Add TXT record
				if(dnsRequestPacket.getQuestions().get(0).getType() == DNS.TYPE_A){
					addTXTRecord(finalAnswerList);
				}

				for(int i=0; i<cnameList.size(); i++){
					finalAnswerList.add(0, cnameList.get(i));
				}

				if(dnsReceivePacket.getAuthorities().size()==0){
					dnsReceivePacket.setAuthorities(lastAuthList);
				}

				if(dnsReceivePacket.getAdditional().size()==0){
					dnsReceivePacket.setAdditional(lastAddList);
				}

				dnsReceivePacket.setAnswers(finalAnswerList);
				dnsReceivePacket.setQuestions(dnsRequestPacket.getQuestions());
				setDNSReplyFlags(dnsReceivePacket);
				finalReplyPacket = new DatagramPacket(dnsReceivePacket.serialize(),dnsReceivePacket.getLength());
			}
		}
	}


		return finalReplyPacket;
	}



	private static DatagramPacket parseRequest(DatagramPacket queryPacket,DNS dnsPacket) throws Exception
	{

		DatagramPacket newQueryPacket, receivedPacket;
		byte[] receiveData = new byte[4096];

		newQueryPacket = new DatagramPacket(queryPacket.getData(), queryPacket.getLength(),rootServer, 53);
		receivedPacket = new DatagramPacket(receiveData,receiveData.length);

		dnsReplySocket.send(newQueryPacket);
		dnsReplySocket.receive(receivedPacket);

		System.out.println("Packet Received!!!");

		return receivedPacket;
	}


	public static void main(String[] args)
	{
		if( args.length != 4 )
		{
			System.out.println( "java edu.wisc.cs.sdn.simpledns.SimpleDNS -r <root server:e.g. a.root-servers.net> -e <ec2 csv>" );
			return ;
		}
		byte[] receiveData = new byte[PACKETSIZE];
		File infile = new File(args[3]);  //hold the csv file
		try
		{
			dnsReplySocket = new DatagramSocket(listenPortDNS); 
			System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());

			ownAddress = InetAddress.getByName("localhost");
			rootServer = InetAddress.getByName(args[1]);
			firstPacket = new DatagramPacket(receiveData, receiveData.length);

			//read in the CSV file
			csvInput = new LinkedList<CSV>(); 
			CSV readin; 
			Scanner scannedIn = new Scanner(infile);
			while(scannedIn.hasNextLine()){
				readin = new CSV(); 

				Scanner scanner2 = new Scanner(scannedIn.nextLine()); 
				scanner2.useDelimiter("/"); 
				readin.setIP(scanner2.next());
				Scanner scanner3 = new Scanner(scanner2.next()); 				
				scanner3.useDelimiter(","); 
				readin.setMask(scanner3.nextInt());
				readin.setLocation(scanner3.next());
				csvInput.add(readin); 
				System.out.println(readin);
			}
			scannedIn.close();


			while(true)                
			{ 
				dnsReplySocket.receive(firstPacket);

				DNS dnsPacket;

				dnsPacket = DNS.deserialize(firstPacket.getData(), (short)firstPacket.getLength());
				System.out.println("Received DNS request:" + dnsPacket.toString());

				if(dnsPacket.getOpcode() != 0 )
				{
					System.out.println("Opcode wrong.");
					break;
				}

				if(dnsPacket.getQuestions().size() != 1 )
				{
					System.out.println("Error in retrieving question");
					break;
				}

			System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
				List<DNSQuestion> dnsQuestionList = dnsPacket.getQuestions();
				dnsType = dnsPacket.getQuestions().get(0).getType();

				switch(dnsType){
					case DNS.TYPE_A:
						break;
					case DNS.TYPE_AAAA:
						break;
					case DNS.TYPE_CNAME:
						break;
					case DNS.TYPE_NS:
						break;
					default:
						System.out.println("Incompatible");
						break;
				}

				if(dnsPacket.isRecursionDesired())
				{
					finalReply = parseRequestRecurse(firstPacket, dnsPacket);
				}
				else
				{
					finalReply = parseRequest(firstPacket, dnsPacket);
				}

			System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
				DNS dnsAnswer = DNS.deserialize(finalReply.getData(), (short)finalReply.getLength());

				System.out.println("Final Answer:  "+dnsAnswer.toString());

				finalReply.setPort(firstPacket.getPort());
				finalReply.setAddress(firstPacket.getAddress());

				dnsReplySocket.send(finalReply);
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

	}
}
