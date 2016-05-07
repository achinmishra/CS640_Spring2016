package csvparse;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class csvread {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		List<CSV> csvInput = new LinkedList<CSV>(); 
		
		CSV readin; 
		String resolvedIP = null; 
		String matchLocation = null; 
		int mask = -1 ; 
		
		File infile = new File(args[0]); 
		
		try {
			Scanner scannedIn = new Scanner(infile);
			while(scannedIn.hasNextLine()){
				scannedIn.useDelimiter(",/");
				readin = new CSV(); 
				readin.setIP(scannedIn.next());
				readin.setMask(scannedIn.nextInt());
				readin.setLocation(scannedIn.next());
				
				csvInput.add(readin); 
				System.out.println(readin);
                
				
				
				
				
			}
			scannedIn.close();
			
			
			//compare the resolved IP with the ec2 pool
			for (CSV curr : csvInput){
				if (curr.ifMatch(resolvedIP)){
					if (curr.getMask() > mask){
						mask = curr.getMask(); 
						matchLocation = curr.getLocation(); 
					}					
				}				
			}
			
			if (mask >= 0) System.out.println(matchLocation); 
			
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
