import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javafx.util.Pair;

//Tool main
public class Tool {
	
	static String input;
	private static Functions func;
	
	public static void main(String[] args) throws IOException {
		Tool anawsTool = new Tool();
		func = new Functions();
		System.out.println("Welcome in DiffServ Tool!");
		while(true) {
			anawsTool.printMenu();
		}
	}
	
	public void printMenu() throws IOException {
		System.out.println("-------------------------------------------------------------------------\n"
				+ "1. Connect to router\n"
				+ "2. Show topology\n"
				+ "3. Configure DiffServ\n"
				+ "4. Define new Class\n"
				+ "5. Show router configurations\n"
				+ "-------------------------------------------------------------------------\n");
		System.out.println("Type .help for manual, .exit to close\n");
		waitForCommand();
	}
	
	public void waitForCommand() throws IOException {
		try {
			input = System.console().readLine();
			whatCommand(input);			
		}catch (NumberFormatException e) {
		}
	}
	
	public void whatCommand(String in) throws IOException {
		if(in.startsWith(".help")) {
			printManual();
		}
		
		if(in.startsWith(".exit"))
			System.exit(0);
		
		
		int cmd = Integer.parseInt(in);
		
		switch(cmd) {
			case 1:
				func.connectToRouter();
				break;
		
			case 2:
				func.showTopology();
				break;
			
			case 3:
				func.configureDF();
				break;
			
			case 4:
				String fileName = func.defineNewClass();
				if(fileName != null) func.verifyNewClass(fileName);
				break;
			
			case 5:
				func.showRunningConf();
				break;
				
			default:
				System.out.println("This command does not exist\n");
				waitForCommand();
				break;
		}	
	}
	
	public void printManual() throws IOException {
		System.out.println("1. Connect to router: allows the admin to connect via ssh to a router.\n" + 
				"2. Show topology: view the network topology using the ospfd daemon.\n" + 
				"3. Configure DiffServ: allows to configure DiffServ on one or all routers of the network " + 
				"in an automated way; once this item is selected, the admin will be asked whether to use " + 
				"the standard classes (Video, Web, Voip and Excess) or to define new ones.\n" + 
				"4. Define new class: allows you to define new service classes for flows classification.\n" + 
				"5. Show router configurations: allows you to view the current configuration " +
				"of the router for the purpose of verification.\n");
		waitForCommand();
	}
	
}
