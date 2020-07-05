
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.util.Pair;
import net.schmizz.sshj.common.IOUtils;
import net.sf.expectit.Expect;

public class Functions{
	
	public String userInput = null;
	public List<String[]> routerDescription;
	public File[] fileList;
	static final String USER = "cisco";
	static final String PASSWORD = "cisco";
	static final String[] VTYCOMMAND = {"/usr/bin/vtysh", "-d", "ospfd", "-c", "show ip ospf database"};
	static final String STDCLASSDIR = "../classes/std/";
	static final String NEWCLASSDIR = "../classes/new/";
	private Set<String> localAddress;
	private String topology;
	private Runtime rt;
	private Map<String, Integer> dscpValues;
	
	
    public Functions() {
    	rt = Runtime.getRuntime();
		localAddress = new HashSet<String>();
		dscpValues = new HashMap<>();
		dscpValues.put("BE", 0);
		dscpValues.put("AF11", 10);
		dscpValues.put("AF12", 12);
		dscpValues.put("AF13", 14);
		dscpValues.put("AF21", 18);
		dscpValues.put("AF22", 20);
		dscpValues.put("AF23", 22);
		dscpValues.put("AF31", 26);
		dscpValues.put("AF32", 28);
		dscpValues.put("AF33", 30);
		dscpValues.put("AF41", 34);
		dscpValues.put("AF42", 36);
		dscpValues.put("AF43", 38);
		dscpValues.put("CS1", 8);
		dscpValues.put("CS2", 16);
		dscpValues.put("CS3", 24);
		dscpValues.put("CS4", 32);
		dscpValues.put("CS5", 40);
		dscpValues.put("CS6", 48);
		dscpValues.put("CS7", 56);
		dscpValues.put("EF", 46);
		
		// get address of all interfaces of this machine
		try {
			Enumeration<NetworkInterface> ifaces;
			ifaces = NetworkInterface.getNetworkInterfaces();
			while(ifaces.hasMoreElements()) {
				NetworkInterface currif = ifaces.nextElement();
				Enumeration<InetAddress> addresses = currif.getInetAddresses();
				while(addresses.hasMoreElements())
				{
					InetAddress addr = addresses.nextElement();
					if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
						localAddress.add(addr.getHostAddress());
					}
				}
			}
		} catch (SocketException e) {
			System.err.printf("Error caught initializing Functions:\n%s\n%s", e.getClass().getName(), e.getMessage());
			System.exit(1);
		}
	}

//*******************************************************************************************************************************************    
    /**
     * Try to connect to a router which address will be asked to the user.
     * @throws IOException in case of connection errors
     */
    public void connectToRouter() throws IOException {
		String userInput = null;
		printRouterList();
		boolean isValidInput = false;
		do {
			System.out.println("Choose the router IP of the list to which you want to connect to (.exit to return back):");
			userInput = System.console().readLine();
			if(userInput.equals(".exit"))
				return;
			isValidInput = getRouterAddresses().contains(userInput);
		} while (isValidInput == false);
		OSPFRouter router = new OSPFRouter(userInput);
		router.connect(USER, PASSWORD);
		Expect exp = router.getExpectIt();
		try {
			do {
				userInput = System.console().readLine();
				exp.sendLine(userInput);
			}while (true);
		}
		catch (Exception e) {
		}
	}
	
//******************************************************************************************************************************************* 
	
	/**
	 * Show the LSA Database retrieved from ospfd.
	 */
	public void showTopology() {
		System.out.println("Show Topology\n");
		while(topology == null || topology.isEmpty()) {
			try {
				Process vtysh = this.rt.exec(VTYCOMMAND);
				if (vtysh == null) {
					System.err.println("Error in command string");
					System.exit(1);
				}
				else {
					topology = IOUtils.readFully(vtysh.getInputStream()).toString();
				}
			} catch (IOException e) {
				System.err.printf("Exception caught: %s\n%s\n", e.getClass().getName(), e.getMessage());
				System.exit(1);
			}
		}
		System.out.println(topology);
	}
	
//******************************************************************************************************************************************* 
	
	/**
	 * Starts the configuration of DiffServ by asking some questions to the user
	 * @throws IOException In case of connection errors 
	 */
	public void configureDF() throws IOException {
		String input1 = null;
		String input2 = null;
		String addr = null;

		System.out.println("Starting DiffServ Configuration wizard...\n");
		printAllClasses();
		while(input1 == null) {
			System.out.println("Do you want to use standard (std) or new classes(new)? (.exit to return back)");
			input1 = System.console().readLine();
			if(input1.equals(".exit"))
				return;
			if(!input1.equals("std") && !input1.equals("new"))
				input1 = null;
		}
		if(input1.equals("new")) {
			File curDir = new File(NEWCLASSDIR);
			fileList = curDir.listFiles();
			if(fileList.length == 0) {
				System.out.println("No admin class defined");
				return;
			}
		}
		
		while(input2 == null) {
			System.out.println("Type:\n"
					+ "<all> to configure DiffServ on every routers in the net\n"
					+ "<one> to configure DiffServ on a single router\n");
			input2 = System.console().readLine();
			if(!input2.equals("one") && !input2.equals("all"))
				input2 = null;
		}
				
		//Checking input1 and input2 content		
		List<String> routerAddrs = getRouterAddresses();
		if(input1.equals("std") && input2.equals("one")) {
			printRouterList();
			while(addr == null) {
				System.out.print("Choose the router (enter the IP address): ");
				addr = System.console().readLine();
				if(!routerAddrs.contains(addr)) 
					addr = null;
			}
			confDF(addr, true);
		}
		else if (input1.equals("std") && input2.equals("all")) {
			System.out.println("\nGetting router list");
			if (routerDescription == null) routerDescription = getRouterDesc();
			if (routerDescription == null) {
				System.err.println("Error retrieving list of router");
				return;
			}
			for(String[] desc: routerDescription) {
				confDF(desc[1], true);
			}
		}
		else if (input1.equals("new") && input2.equals("one")) {
			
			printRouterList();
			while(addr == null) {
				System.out.print("Choose the router (enter the IP address): ");
				addr = System.console().readLine();
				if(!routerAddrs.contains(addr)) 
					addr = null;
			}
			confDF(addr, false);
		}
		else if (input1.equals("new") && input2.equals("all")) {
			
			System.out.println("\nGetting router list");
			if (routerDescription == null) routerDescription = getRouterDesc();
			if (routerDescription == null) {
				System.err.println("Error retrieving list of router");
				return;
			}
			for(String[] desc: routerDescription) {
				confDF(desc[1], false);
			}
		}
	}
	
//******************************************************************************************************************************************* 	
	/**
	 * Allow the user to define a new class by using a wizard or by inserting lines themself.
	 * @return The name of the file where the user defined commands is saved
	 */
	public String defineNewClass() {
		printNewClasses();
		System.out.println("\nEnter name of the new class or those you want redefine  (.exit to return back): \n");
		String filename = System.console().readLine();
		if(filename.equals(".exit"))
			return null;
		try {
			PrintWriter classFile = new PrintWriter(new File(NEWCLASSDIR + filename));
			classFile.println("class " + filename);
			System.out.println("Do you prefer to be guided through the class definition?\n" 
								 + "yes - the wizard will allow you to define basic features such as shaping,\n"
								 + "      policing and enabling RED\n"
								 + "no - you can write your own class line by line\n");
			String userResp = null;
			do{
				System.out.println("Response (yes/no): ");
				userResp = System.console().readLine();
			}while(!(userResp.equals("yes") || userResp.equals("no")));
			try {
				// User wants them to be guided into the configuration
				if (userResp.equals("yes")) {
					do {
						System.out.print("\n\nWrite bandwidth limit (0 = no limit, max 100): ");
						userResp = System.console().readLine();						
					} while (!isInsideBound(userResp, 0, 100));
					if (!userResp.equals("0")) {
						classFile.println("bandwidth percent " + userResp);
					}
					do {
						System.out.print("\n\nWrite traffic shaping on average speed of (bps, 0 = no shaping): ");
						userResp = System.console().readLine();						
					} while (!isInsideBound(userResp, 0, Integer.MAX_VALUE));
					if (!userResp.equals("0")) {
						classFile.println("shape average  " + userResp);
					}
					do {
						System.out.println("\n\nApply random early detection to class? (yes/no) ");
						userResp = System.console().readLine();						
					} while (!(userResp.equals("yes") || userResp.equals("no")));
					if(userResp.equals("yes")) {
						classFile.println("random-detect");
						classFile.println("no random-detect precedence-based");
					}
					boolean checked = false;
					do {
						System.out.println("Avaiable DSCP values: ");
						System.out.println("DSCP\t\tDecimal");
						String ss;
						String[] spl;
						for(Map.Entry<String, Integer> i : dscpValues.entrySet()) {
							//System.out.println(i);
							ss = i.toString(); //convert map to string
							spl = ss.split("="); //split the string
							System.out.println(spl[0] + "\t\t" + spl[1]);
						}
						System.out.print("\n\nSet a DSCP value for this class (literal or numerical, 0 is default): ");
						userResp = System.console().readLine().toUpperCase();
						try {
							int dscpIntValue = Integer.parseInt(userResp);
							checked = this.dscpValues.containsValue(dscpIntValue) ? true : false; 
						}
						catch (NumberFormatException e){
							checked = this.dscpValues.containsKey(userResp) ? true : false;
						}
					} while (!checked);
					if(!(userResp.equals("0"))) {
						classFile.println("set ip dscp " + userResp);
					}
				}
				else {
					System.out.println("Commands written here must be valid cisco IOS commands.\n"
							+ "Example to define a new classification mapping:\n\n"
							+ "! define new classification, all hosts matching 192.168.1.* are classified\n"
							+ "access-list 1 permit 192.168.1.0 0.0.0.255\n"
							+ "class map match-all VOIP\n"
							+ " match access-group 1\n\n"
							+ "Write line by line your class definition below. End with .exit\n\n"
							+ "class " + filename
							);
					String input = null;
					do {
						input = System.console().readLine();
						if (input.equals(".exit") == true) break;
						classFile.println(input);
					} while (true);
				}				
			}
			finally {
				classFile.close();
			}
		} catch (FileNotFoundException e) {
			System.err.println("Cannot create class file on the filesystem");
			System.exit(1);
		}
		return filename;
	}
	
	/**
	 * Check if the string s can be converted to an integer and if this integer is inside bound (excluded).
	 * @param s	the string which has to contain an integer number
	 * @param lowerBound the lower bound to check against s, that is s must be greater than lowerBound
	 * @param upperBound the upper bound to check against s, that is s must be lower than upperBound
	 * @return
	 */
	private boolean isInsideBound(String s, int lowerBound, int upperBound) {
		if (upperBound < lowerBound) {
			throw new IllegalArgumentException("Lower bound can't be less than upper bound");
		}
		int num;
		try {
			num = Integer.parseInt(s);
			if (num > upperBound || num < lowerBound) return false;
			else return true;
		}
		catch (NumberFormatException e) {
			return false;
		}
	}
	
//*******************************************************************************************************************************************

	/**
	 * Connect to a router and ask it for its configuration to be shown to the user.
	 * @throws IOException if a connection errors occours
	 */
	public void showRunningConf() throws IOException {
		printRouterList();
		userInput = null;
		while(userInput == null) {
			System.out.println("Choose the router IP of the list to which you want the running-conf:");
			userInput = System.console().readLine();
			if (!getRouterAddresses().contains(userInput)) {
				userInput = null;
			}
		}
		OSPFRouter router = new OSPFRouter(userInput);
		router.connect(USER, PASSWORD);
		String conf = router.getRunningConfig();
		router.disconnect();
		System.out.println(conf);
	}
	
	
//******************************************************************************************************************************************* 
	
	/**
	 * Get the description of the router. It includes the hostname, the router ID and if the router is 
	 * an Area Border ROuter or not.
	 * @return List of router descriptions. Return null if the connection to all the router has failed.
	 */
	private List<String[]> getRouterDesc(){
		try {
			List<String[]> descriptions = new LinkedList<>();
			List<String> addresses = null;
			Runtime rt = Runtime.getRuntime();
			do {
				Process vtysh = rt.exec(VTYCOMMAND);
				if (vtysh == null) {
					System.err.println("Error in command string");
					return null;
				}
				else {
					addresses = getRouterIds(vtysh.getInputStream());
				}
			}while (addresses == null);
			addresses.forEach(ip -> {
				String[] routerDesc = new String[3];	// hostname, ip and if ABR or not
				OSPFRouter router = new OSPFRouter(ip);
				try {
					router.connect(USER, PASSWORD);
					routerDesc[0] = router.getHostname();
					routerDesc[1] = ip;
					routerDesc[2] = router.isBorderRouter() ? "ABR" : "";
					router.disconnect();
					descriptions.add(routerDesc);
				} 
				catch (IOException e) {
					System.err.println("Error connecting to " + ip);
				}
			});
			return descriptions;
		}
		catch (Exception e) {
			System.err.printf("Exception caught: %s\n%s\n", e.getClass().getName(), e.getMessage());
			return null;
		}
	}
	
	/**
	 * Given the ip address as parameter, this function will connect to that ip (must be a valid Cisco 
	 * router IP address) and return the hostname of the router.
	 * @param ip the address of the router the user wants its name to know.
	 * @return the hostname of the router
	 */
	private String getRouterHostname(String ip) {
		List<String> routerHostname = new LinkedList<>();
		String name = null;
		if (routerDescription ==  null) routerDescription = getRouterDesc();
		for (String[] desc : routerDescription) {
			routerHostname.add(desc[1]);
			if(desc[1].equals(ip)) {  
				name = desc[0]; 
				break;
			}
		}
		return name;
	}
	
//******************************************************************************************************************************************* 
	
	/** 
	 * Retrieve the list of the router ip from the input stream passed
	 * @param s InputStream from vtysh command
	 * @return the list of IPs that belong to the routers of the network
	 * @throws IOException
	 */
	private List<String> getRouterIds(InputStream s) throws IOException{
		String splitStartToken = "Router Link States";
		String splitEndToken = "Net Link States";
		Pattern pattern = Pattern.compile("^(\\d){1,3}\\.(\\d){1,3}\\.(\\d){1,3}\\.(\\d){1,3}", Pattern.MULTILINE);
		String out = IOUtils.readFully(s).toString();
		if (out.isEmpty() || out == null) {
			return null;
		}
		if (!out.contains(splitEndToken)) {
			return null;
		}
		if (topology == null || !topology.equals(out)) {
			System.out.println("Updating topology");
			topology = out;
		}
		String routerLinkTable = out.substring(out.indexOf(splitStartToken), out.indexOf(splitEndToken));
		Matcher matcher = pattern.matcher(routerLinkTable);
		List<String> routerIds = new ArrayList<String>();
		while (matcher.find()) {
			String id = matcher.group();
			// Add address which doesn't belong to this machine
			if (!localAddress.contains(id)) {				
				routerIds.add(id);
			}
		}
		return routerIds;
	}
	
//******************************************************************************************************************************************* 	
	/**
	 * This function prints the router list of the network the tool is attached to. This gives some
	 * informations about hostname, ip address and if the router is a border router or not.
	 */
	private void printRouterList() {
		System.out.println("Getting router list");
		if(routerDescription == null) {
			routerDescription = getRouterDesc();
		}
		if (routerDescription == null) {
			System.err.println("Error retrieving list of router");
			return;
		}
		// Print router list and make the user to choose
		System.out.println("\n---   List of available routers   ---");
		System.out.println("Hostname\tIP address\tABR?");
		for(String[] desc: routerDescription) {
			System.out.println(desc[0] + "\t\t" + desc[1] + "\t" + desc[2]);
		}
		System.out.println();
	}	
//******************************************************************************************************************************************* 	
	/**
	 * Print the list of the new classes, that is the user defined classes by using this tool.
	 */
	public void printNewClasses() {
		System.out.println("\n " + " List of available classes:\n");
		File curDir = new File(NEWCLASSDIR);
		fileList = curDir.listFiles();
		for(int i = 0; i<fileList.length; i++) {
			System.out.println(i+1 + ": " + fileList[i].getName());
		}
	}
	
	/**
	 * Print standard and custom classes.
	 */
	public void printAllClasses() {
		System.out.println("STANDARD CLASSES:\n"
				+ "- VOIP\n"
				+ "- VIDEO\n"
				+ "- WEB\n"
				+ "- EXCESS");
		System.out.println("ADMIN DEFINED CLASSES:");
		File curDir = new File(NEWCLASSDIR);
		fileList = curDir.listFiles();
		for(int i = 0; i<fileList.length; i++) {
			System.out.println("-"+fileList[i].getName());
		}
	}
	
//*******************************************************************************************************************************************	
	/**
	 * @return the list of the IP address of any router attached to the network.
	 */
	private List<String> getRouterAddresses(){
		List<String> routerAddrs = new LinkedList<>();
		if (routerDescription ==  null) routerDescription = getRouterDesc();
		for (String[] desc : routerDescription) {
			routerAddrs.add(desc[1]);
		}
		return routerAddrs;
	}
	
	/**
	 * Return a list of all the integers contained in s
	 * @param s A String that contains some integers
	 * @return All integers inside the string s
	 */
	private List<Integer> getIntValues(String s) {	
		Matcher m = Pattern.compile("(\\d+)", Pattern.MULTILINE).matcher(s);
		List<Integer> valueList = new ArrayList<>();
		while(m.find()) {
			valueList.add(Integer.parseInt(m.group()));
		}
		return valueList;
	}
	
	/**
	 * Allow the user to check the class file passed by parameter using an interactive way to do
	 * this job.
	 * @param classFileName The file name user wants it to check
	 * @throws IOException In case of reading from filesystem errors
	 */
	public void verifyNewClass(String classFileName) throws IOException {
		Path classPath = Paths.get(NEWCLASSDIR, classFileName);
		List<String> rows = Files.readAllLines(classPath);
		boolean saveFile = false;
		System.out.println("The content of the class " + classFileName + " is the following:\n");
		boolean userConfirmed = false;
		do {
			printClassContent(rows);
			System.out.println("Insert the line or the lines (comma separated list) you want them to be changed (type no or RETURN if it is all right):");
			String userInput = System.console().readLine();
			if ("no".equals(userInput) || "".equals(userInput)) {
				break;
			}
			if (isCommaSeparated(userInput)) {

				int maxRows = rows.size();
				for (Integer rowNum : getIntValues(userInput)) {
					try {
						String oldLine = rows.get(rowNum - 1); // We count from 0, user from 1
						System.out.println("Old row value: ");
						System.out.println(oldLine);
						System.out.println("Insert new line below :");
						String newLine = System.console().readLine();
						if (!oldLine.equals(newLine)) {
							rows.set(rowNum - 1, newLine);
						}
					}
					catch (NumberFormatException | IndexOutOfBoundsException e ) {
						System.err.println("You have inserted an invalid row number for this file: " + userInput);
						System.err.println("Max number of rows: " + maxRows);
					}
				}
				printClassContent(rows);
				System.out.println("Do you want to save the changes? (yes/no)");
				do {
					userInput = System.console().readLine();
				} while (!"yes".equals(userInput) && !"no".equals(userInput));
				if ("yes".equals(userInput)) {
					userConfirmed = saveFile = true;
				}
			}
		} while (userConfirmed == false);
		if (saveFile == true) {
			Files.write(classPath, rows);
		}			
	}
	
	
	/**
	 * Check if the input string s is a valid comma separated list of values
	 * @param s	string to check
	 * @return true if s is a valid comma separated list, false otherwise
	 */
	private boolean isCommaSeparated(String s) {
		Pattern pattern = Pattern.compile("^(\\d+)(,\\s*\\d+)*$|^no$");
		Matcher match = pattern.matcher(s);
		return match.matches();
	}
	
	
	/**
	 * Print the list fileContent with row number
	 * @param fileContent a list of string. In the context of this function it is a list
	 * of commands to be printed.
	 */
	private void printClassContent(List<String> fileContent) {
		int rowNum = 0;
		for (String row : fileContent) {
			rowNum ++; 
			System.out.printf("%d %s\n", rowNum, row);
		}
	}
	
		
//*******************************************************************************************************************************************
	/**
	 * Connects and makes the user to choose one or more standard classes to define on router <b>ip</b>
	 * @param ip	The IP address of the router on which to apply the standard class/classes 
	 * @throws IOException If a connection error occours
	 */
	private void confDF(String ip, boolean std) throws IOException {
		boolean isBR;
		List<String> selectedClasses = confMenu(std);
		
		// Connect to the router to retrieve only the interface network informations. This is needed because 
		// the configuration process can take longer time than the ssh disconnection timeout.
		OSPFRouter router = new OSPFRouter(ip);
		router.connect(USER, PASSWORD);
		List<Pair<String, String>> ifaceList = router.getInterfaceNetwork();
		router.disconnect();
		
		// Ask the user all the parameters needed for the conf serv to be set up.
		System.out.println("The following is the list of interfaces of " + ip + " ( " + getRouterHostname(ip) + " ) ");
		System.out.println("#  Interface\t\tAddress");
		int i = 1;
		for(Pair<String, String> iface : ifaceList) {
			System.out.println(i + "  " + iface.getKey() + "\t\t" + iface.getValue());
			i ++;
		}
		String input;
		int ifaceListSize = ifaceList.size();
		do {// At least there is an interface at this point, the one with ip as address
			System.out.printf("Choose the interface (or interfaces) to apply the diffserv" +
							  (selectedClasses.size() > 1 ? " classes" : " class") +
							   " (1-%d): ", ifaceListSize);
			input = System.console().readLine();
		}while (!isCommaSeparated(input) || !checkClass(input, 1, ifaceListSize));
		List<String> commands = null;
		router.connect(USER, PASSWORD);
		isBR = router.isBorderRouter();
		for(Integer ifaceNum: getIntValues(input)) {
			int selectedIface = ifaceNum.intValue() - 1;
			if (selectedIface >= 0 && selectedIface < ifaceListSize) {
				commands = getClassConfigureCommands(ifaceList.get(selectedIface),
													 selectedClasses, isBR, std);
				
			}
			router.configureRouter(commands);
		}
		router.disconnect();
	}
	
	
	/**
	 * Read the file of the standard classes and return the list of command from it
	 * @param className The name of the class file to be read
	 * @param std If the class is a standard class or a custom one.
	 * @return	List of commands written into the file
	 * @throws IOException If reading the file makes some problems
	 */
	private List<String> readClassCommands(String className, boolean std) throws IOException{
	
		return Files.readAllLines(Paths.get((std ? STDCLASSDIR : NEWCLASSDIR), className));
		
	}
	
	
	/** 
	 * Create a list of commands to be applied to a Cisco router. Commands are different if the router is
	 * a Border Router or not and if the class to get commands to is a standard class or a custom one.
	 * It return a complete command list also if the user ask the function to return commands from more than
	 * one class
	 * @param pair	a Pair object which contains the name of interface and the IP address of it
	 * @param classes list of name of classes
	 * @param br true if the router is a Border Router or false otherwise
	 * @param std true if the class the user wants the command to is a standard class
	 * @return the complete list of commands needed to set a router up.
	 * @throws IOException in case of file reading error
	 */
	public List<String> getClassConfigureCommands(Pair<String, String> pair, List<String> classes, boolean br, boolean std) throws IOException {
		List<String> commands = new LinkedList<>();
		int accessListNum = 101;
		
		if(br) {
			// Define different access lists, one for each standard class
			for (String dsClass : classes) {
				if(std) {
					switch (dsClass) {
						case "WEB":
							commands.add("access-list " + accessListNum + " permit tcp any any range www 81");
							break;
						
						case "VIDEO":
						case "VOIP":
							commands.add("access-list " + accessListNum + " permit udp any any");
							break;
						
						case "EXCESS":
							commands.add("access-list " + accessListNum + " permit ip any any");
							break;
						
						default:
							throw new IllegalArgumentException("no standard class called " + dsClass);
				    }
				}
				else {
					// TCP/UDP choise
					System.out.println("Do you want to apply TCP protocol to interface " + pair.getKey() + " ? (yes/no):");
					String input;
					do {
						input = System.console().readLine();
					} while (!input.equalsIgnoreCase("yes") && !input.equalsIgnoreCase("no"));
					commands.add("access-list " + accessListNum + " permit " + (input.equalsIgnoreCase("yes") ? "tcp " : "udp ") + "any any");
				}
				commands.add("class-map match-all " + dsClass);
				commands.add("match access-group " + accessListNum);
				commands.add("exit");
				accessListNum ++;
			}
		}
		else {
			for(String dsClass: classes) {
				if(std) {
					switch (dsClass) {
						case "WEB":
							commands.add("class-map match-all WEB");
							commands.add("match dscp af33");
							break;
						
						case "VIDEO":
							commands.add("class-map match-all VIDEO");
							commands.add("match dscp af13");
						case "VOIP":
							commands.add("class-map match-all VOIP");
							commands.add("match dscp ef");
							break;
						
						case "EXCESS":
							commands.add("class-map match-all EXCESS");
							commands.add("match dscp af43");
							break;
						
						default:
							throw new IllegalArgumentException("no standard class called " + dsClass);
				    }
					commands.add("exit");
				}
				else {
					commands.add("class-map match-all " + dsClass);
					List<String> classcommands = readClassCommands(dsClass, std);
					for(String command : classcommands) {
						Matcher match = Pattern.compile("set ip dscp (.+)").matcher(command);
						if(match.matches()) {
							commands.add("match dscp " + match.group(1));
							commands.add("exit");
						}							
					}
				}
			}
		}
		// Set the policy name, i.e. Et11 
		String ifaceName = pair.getKey();
		String policyName = (ifaceName.substring(0, 2) + 
							 ifaceName.substring(ifaceName.length() - 3, ifaceName.length()))
							.replaceAll("/","");
		commands.add("policy-map " + policyName);
		for (String dsClass : classes) {
			try {
				if(std) {
					commands.addAll(readStdClassCommands(dsClass, true));					
				}
				else {
					commands.addAll(readClassCommands(dsClass, false));					
				}
				//commands.add("exit");
			}
			catch (IOException e) {
				System.err.println("Can't read file " + dsClass);
				return null;
			}
		}
		commands.add("exit"); // I COMANDI EXIT DI RIGA 753 E 803
		commands.add("exit");// LI FACCIO TUTTI E DUE QUI
		
		System.out.println("Setting service-policy...");
		String userInput;
		do {
			System.out.println("Apply the service policy to the input traffic? (yes/no)");
			userInput = System.console().readLine().toLowerCase();
		}while (!"no".equals(userInput) && !"yes".equals(userInput));
		
		//set service policy input only for BR
		//set service policy output only for CR
		commands.add("interface " + ifaceName);
		commands.add("service-policy " + (userInput.equals("yes") ? "input " : "output ") + policyName);
		commands.add("exit");
		
		return commands;
	}
	
	
	/**
	 * Return the list of commands based on the name of the standard class stdClass and if the router
	 * is a Border Router or not
	 * @param stdClass the name of the standard class to get the command from
	 * @param isBr true if the router is a border router or not
	 * @return A list of commands for configuring the standard class into the router
	 */
	public List<String> readStdClassCommands (String stdClass, boolean isBr) {
		List<String> commands = new LinkedList<>();
		if (isBr == true) {
			commands.add("class " + stdClass);
			switch (stdClass) {
			case "WEB":
				commands.add("set ip dscp 0");
				break;
			
			case "VIDEO":
				commands.add("set ip dscp AF13");
				break; 
				
			case "VOIP":
				commands.add("set ip dscp EF");
				break;
			
			case "EXCESS":
				commands.add("set ip dscp AF43");
				break;
			
			default:
				throw new IllegalArgumentException("no standard class called");
			}
		}
		return commands;
	}
	
	/**
	 * Check if the string passed is below the min or over the max included
	 * @param input the input string. It has to be a comma separated value or a value alone
	 * @param min the minimum value admissable for values of input
	 * @param max the maximum value admissable for values of input
	 * @return true if the comma separated list input has all its value inside the interval [min, max]
	 */
	
	private boolean checkClass(String input, int min, int max) {
		boolean okstd = true;
		String[] splits = null;
		if(input.contains(",")) {
			splits = input.split(",");
			for(int i = 0; i < splits.length; i++)
				if(Integer.parseInt(splits[i]) > max || Integer.parseInt(splits[i]) < min) okstd = false;
		}
		else if(Integer.parseInt(input) > max || Integer.parseInt(input) < min) okstd = false;
		return okstd;
	}
	
	
	/**
	 * Print a menu and asks the user which class (custom or standard) to apply to a router.
	 * @param std true if this function has to print only standard classes or only custom classes
	 * @return a list of classes being chosen by the user
	 */
	private List<String> confMenu(boolean std) { 
		List<String> cl = new ArrayList<>();
		if(std) {
			
			String userInput;
			do {			
				System.out.println("Choose which class or classes to apply: \n"
						+ "1- VoIP\n"
						+ "2- Video\n"
						+ "3- Web\n"
						+ "4- Excess\n"
						+ "You can insert a comma separated list (of number) if you want to apply more than one class");
				userInput = System.console().readLine();
			} while (!isCommaSeparated(userInput) || !checkClass(userInput, 1, 4));

			for (Integer value : getIntValues(userInput)) {
				switch (value.intValue()) {
					case 1:
						if (!cl.contains("VOIP")) {
							cl.add("VOIP");
						}
						break;
					case 2:
						if (!cl.contains("Video")) {
							cl.add("VIDEO");
						}
						break;
					case 3:
						if (!cl.contains("WEB")) {
							cl.add("WEB");
						}
						break;
					case 4:
						if (!cl.contains("EXCESS")) {
							cl.add("EXCESS");
						}
						break;
					default:
						break;
				}
			}
		}
		else {
			
			String userInput;
			do {		
				System.out.println("Choose which class or classes to apply: ");
				printNewClasses();
				System.out.println("You can insert a comma separated list (of number) if you want to apply more than one class");
				userInput = System.console().readLine();
				
			} while (!isCommaSeparated(userInput) || !checkClass(userInput, 1, fileList.length));
			for (Integer value : getIntValues(userInput)) {
				if(!cl.contains(fileList[value-1].getName()))
					cl.add(fileList[value-1].getName());
			}
		}
		
		return cl;
	}
	
//*******************************************************************************************************************************************
	
}





