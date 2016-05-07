package edu.wisc.cs.sdn.simpledns;

import java.net.*;
import java.io.*;
import edu.wisc.cs.sdn.simpledns.packet.*;
import java.util.*;

public class SimpleDNS {

	private final static int PACKETSIZE = 1500;
	private static String rootServer;

public static String findIPAddr( String domain, String nameserver )
{
                // this will recursively follow records / cnames until we achieve an a record (ip address)

                try {
			System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
                        int port = 53;
                        DatagramSocket newSocket = new DatagramSocket( port, InetAddress.getByName(nameserver) );

                // Create a packet
                        DatagramPacket packet = new DatagramPacket( new byte[PACKETSIZE], PACKETSIZE );

                        packet.setAddress(InetAddress.getByName(nameserver));

                        DNS newPayload = new DNS();
                        newPayload.setQuery( true );
                        newPayload.setOpcode( DNS.OPCODE_STANDARD_QUERY );
                        newPayload.addQuestion( new DNSQuestion(domain, DNS.TYPE_A) );

                        packet.setData( newPayload.serialize() );

                        newSocket.send( packet ) ;


                // Receive a packet (blocking)
                DatagramPacket packet_zwei = new DatagramPacket( new byte[PACKETSIZE], PACKETSIZE );
                        newSocket.receive(packet_zwei);

                        //parse response
                        DNS response = DNS.deserialize( packet_zwei.getData(), packet_zwei.getLength() );

                        List<DNSResourceRecord> answers = response.getAnswers();
                        List<DNSResourceRecord> authorities = response.getAuthorities();

                        List<DNSResourceRecord> aRecords = new LinkedList<DNSResourceRecord>();
                        List<DNSResourceRecord> aaaaRecords = new LinkedList<DNSResourceRecord>();
                        List<DNSResourceRecord> nsRecords = new LinkedList<DNSResourceRecord>();
                        DNSResourceRecord cName = null;
	
			if( answers.size() >= 1 ){
                                // check for a good answer

                                for( DNSResourceRecord currAnswer : answers ){

                                        short answerType = currAnswer.getType();

                                        if( answerType == DNS.TYPE_A ){
                                                aRecords.add( currAnswer );
                                        } else if ( answerType == DNS.TYPE_AAAA ){
                                                aaaaRecords.add( currAnswer );
                                        } else if ( answerType == DNS.TYPE_NS ){
                                                nsRecords.add( currAnswer );
                                        } else if ( answerType == DNS.TYPE_CNAME ){
                                                cName = currAnswer;
                                        }

                                }

                                if( aRecords.size() >= 1 ){
                                        return aRecords.get(0).getName();
                                }

                                if( cName != null ){
                                        return findIPAddr( cName.getName(), nameserver );
                                } else {
                                        System.out.print("Error!");
                                        throw new Exception();
                                }

                        }

			if( authorities.size() >= 1 ) {
                                if( nsRecords.size() >= 1 ){
                                        return findIPAddr( domain, findIPAddr( nsRecords.get(0).getName(), rootServer ) );
                                } else {
                                        System.out.println("Error!");
                                        throw new Exception();
                                }
                        }
                        System.out.println("IP MESSED GOOFD");
                        throw new Exception();
                } catch( Exception e ) {
                        System.out.println( e ) ;
                        e.printStackTrace();
                }

                return "8.8.8.8";
}

	public static void parseRequest(DNS dnsRequest, DatagramSocket socket, String nameServer)
	{
			System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
		if( dnsRequest.getOpcode() != 0 )
		{
                        System.out.println("Opcode wrong.");
                        return;
                }
		
		if( dnsRequest.getQuestions().size() != 1 )
		{
                        return;
                }

                short dnsType = dnsRequest.getQuestions().get(0).getType();

		System.out.println("Dns type: " + dnsType);

                if(dnsType != DNS.TYPE_A && dnsType != DNS.TYPE_AAAA && dnsType != DNS.TYPE_NS && dnsType != DNS.TYPE_CNAME )
		{
                        System.out.println("Invalid type of DNS request");
                        return;
                }
		
		try
		{
			//int port = 53;
			System.out.println(nameServer);
			System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());

			//DatagramSocket socket = new DatagramSocket(port, InetAddress.getByName(nameServer));
			byte[] receivebuf = new byte[1500];
			DatagramPacket packet = new DatagramPacket(receivebuf,PACKETSIZE, InetAddress.getByName(nameServer), 53);
//			packet.setAddress(InetAddress.getByName(nameServer));
//			packet.setData(dnsRequest.serialize());
			socket.send(packet);

			//receive reply from nameServer = rootServer in first iteration 

			DatagramPacket replyPacket = new DatagramPacket( new byte[PACKETSIZE], PACKETSIZE );
			socket.receive(replyPacket);


			//parse response
			DNS dnsResponse = DNS.deserialize(replyPacket.getData(), replyPacket.getLength());
			System.out.println("QUERY RES: " + dnsResponse);

			System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());

			List<DNSResourceRecord> answers = dnsResponse.getAnswers();
			List<DNSResourceRecord> authorities = dnsResponse.getAuthorities();
			List<DNSResourceRecord> additional = dnsResponse.getAdditional();
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
					// create a new response, properly add the answers section (usually includes CNAME for no reason.)
					byte[] payload = dnsResponse.serialize();
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

					socket.send(new DatagramPacket(payload, payload.length));
					return;

				} else 
				{
					// need to recurse on a given CNAME
					dnsRequest.getQuestions().get(0).setName(cName.getName());
					parseRequest(dnsRequest, socket, nameServer);
				}
			}
			if(authorities.size() >= 1)
			{
			System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
				String new_nameserver = null;
				//List<DNSResourceRecord> dnsAuthorities = dnsResponse.getAuthorities();  
				List<DNSResourceRecord> dnsAdditionals = dnsResponse.getAdditional();
				//go for new name server and therefore adding third argument in parseRequest function
				//edit it later
				//parseRequest(dnsRequest, socket, ipAddressPlz( authorities.get(0).getName(), ROOTSERVER ) );

				for(DNSResourceRecord current : authorities){
					for(DNSResourceRecord cur : dnsAdditionals){
						if (current.getName() == cur.getName())
						{
							if(cur.getType() == DNS.TYPE_A)
							{
								new_nameserver = cur.getData().toString();
							}
						}
					}
				}
				if(new_nameserver != null)
				{
			System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
					parseRequest(dnsRequest, socket, new_nameserver);
				}
				else
				{
			System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
					new_nameserver = findIPAddr(authorities.get(0).getName(), rootServer);
					parseRequest(dnsRequest, socket, new_nameserver);
				}
				return;
			}
			System.out.println(additional.get(0).getName());
			parseRequest(dnsRequest, socket, additional.get(0).getName());

		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public static void main(String[] args)
	{
		if( args.length != 4 )
		{
			System.out.println( "java edu.wisc.cs.sdn.simpledns.SimpleDNS -r <root server ip: 198.41.0.4 > -e <ec2 csv>" );
			return ;
		}
		rootServer = args[1];
		byte[] receiveData = new byte[PACKETSIZE];

		try
		{ 
			DatagramSocket serverSocket = new DatagramSocket(8053); 
			System.out.println("Method: " + Thread.currentThread().getStackTrace()[1].getMethodName() + " Line number: " + Thread.currentThread().getStackTrace()[1].getLineNumber());
			while(true)                
			{ 
				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				serverSocket.receive(receivePacket);
				DNS dnsPacket;
				dnsPacket = DNS.deserialize(receivePacket.getData(), receivePacket.getLength() );
				parseRequest(dnsPacket, serverSocket, rootServer);
				serverSocket.send(receivePacket);
			}
		}
		catch (Exception e)
		{
			System.err.println("Exception:  " + e);
			e.printStackTrace();
		}


	}
}

