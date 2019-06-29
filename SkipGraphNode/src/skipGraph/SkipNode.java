package skipGraph;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.TreeMap;

import blockchain.Block;
import blockchain.Transaction;
import hashing.HashingTools;
import hashing.Hasher;
import signature.DigitalSignature;

public class SkipNode extends UnicastRemoteObject implements RMIInterface{
	
	
	private static final long serialVersionUID = 1L;
	private static String address;
	private static String nameID;
	private static int numID;
	private static String IP ;
	private static NodeInfo[][][] lookup ;
	private static int maxLevels = 5; 
	private static int maxData = 100;
	private static int dataNum = 0 ; 
	private static String introducer; 
	private static int RMIPort = 1099;
	private static Scanner in = new Scanner(System.in);
	private static TreeMap<Integer,Integer> dataID = new TreeMap<>();
	private static ArrayList<NodeInfo> data = new ArrayList<>();
	private static ArrayList<Transaction> transactions = new ArrayList<>();
	private static ArrayList<Block> blocks = new ArrayList<>();
	private static DigitalSignature digitalSignature;
	private static Hasher hasher;
	private static HashMap<String,Integer> blkIdx = new HashMap<>();
	private static HashMap<Integer,String> view = new HashMap<>();
	private static HashMap<Integer,Integer> viewBalance = new HashMap<>();
	private static HashMap<Integer,Integer> viewMode = new HashMap<>();
	private static int mode ; // 1: Honest, 0: Malicious
	private static int balance = 20;
	private static final int VALIDATION_FEES = 10;
	private static int VAL_THRESHOLD = 2;
	public static final int TRUNC = 6;
	
	public static void main(String args[]) {
		
		
		lookup = new NodeInfo[maxLevels+1][2][maxData]; // Initialize size of lookup table
		setInfo();
		try {
			
			SkipNode skipNode = new SkipNode();
			
			Registry reg = LocateRegistry.createRegistry(RMIPort);
			reg.rebind("RMIImpl", skipNode);
			log("Rebinding Successful");
			
			while(true) {
				printMenu();
				skipNode.ask();
			}
			
		}catch(RemoteException e) {
			System.out.println("Remote Exception in main method. Terminating.");
			e.printStackTrace();
			System.exit(1);
		}catch(IOException e){
			log("Error in Rebinding");
			e.printStackTrace();
		}
		in.close();
		
	}
	/*
	 * Constructor for SkipNode class needed for RMI setup
	 */
	protected SkipNode() throws RemoteException{
		super();
		try {
			String st = Inet4Address.getLocalHost().getHostAddress();
			System.setProperty("java.rmi.server.hostname",st);
			System.out.println("RMI Server proptery set. Inet4Address: "+st);
		}catch (UnknownHostException e) {
			System.err.println("Unknown Host Exception in constructor. Please terminate the program and try again.");
		}
	}
	/*
	 * This method initializes the information of the current node
	 * and prints them to console
	 */
	public static void setInfo() {
		hasher = new HashingTools();
		digitalSignature = new DigitalSignature();
		String numInput ;
		log("Enter the address of the introducer:");
		introducer = get();
		while(!(introducer.equalsIgnoreCase("None") || validateIP(introducer))) {
			log("Invalid IP. Please enter a valid IP address ('none' if original node): ");
			introducer = get();
		}
		log("Enter RMI port: ");
		numInput = get();
		while(!numInput.matches("0|[1-9][0-9]*")) {
			log("Invalid port. Enter a valid port number for RMI:");
			numInput = get();			
		}
		RMIPort = Integer.parseInt(numInput);
		
		try { // Assign address and IP 
			address = Inet4Address.getLocalHost().getHostAddress() +":"+ RMIPort; //Used to get the current node address.
			IP = Inet4Address.getLocalHost().getHostAddress();
			log("My Address is :" + address);
		}catch(UnknownHostException e) {
			System.out.println("Couldn't fetch local Inet4Address. Please restart.");
			System.exit(0);
		}
		
		// The nameID and numID are hash values of the address
		nameID = hasher.getHash(address,TRUNC);
		numID = Integer.parseInt(nameID,2);
		
		log("Specify mode of node, enter 1 for HONEST, 0 for MALICIOUS");
		mode = Integer.parseInt(get());
		while(mode != 0 && mode != 1) {
			log("Incorrect input. Specify mode of node, enter 1 for HONEST, 0 for MALICIOUS");
			mode = Integer.parseInt(get());
		}
		// In case the introducer to this node is null, then the insert method
		// will not be called on it, so we manually add it to the data list and 
		// map index in the data array with its numID.
		if(introducer.equalsIgnoreCase("none")) {
			data.add(new NodeInfo(address,numID,nameID));
			dataID.put(numID, 0);
			dataNum++;
		}
		log("Your INFO:\nnameID: "+nameID+"\nnumID: "+numID+"\nintroducer: "+introducer);
	}
	
	/*
	 * This method prints the options for user controlling the node to choose.
	 * More options can be appended but require the modification of ask() method
	 */
	public static void printMenu() throws IOException{
        log("Node at the address: " + address);
        log("Name ID: "+ nameID +" Number ID: " + numID);
        log("Choose a query by entering it's code and then press Enter");
        log("1-Insert Node");
        log("2-Insert Transaction");
        log("3-Insert Block");
        log("4-Search By Name ID");
        log("5-Search By Number ID");
        log("6-Validate a transaction");
        log("7-Print the Lookup Table");
	}
	
	/*
	 * Gets the type of operation to be executed from the user
	 * and executes the corresponding operation.
	 */
	public void ask() {
        String input = get();
        if(!input.matches("[1-7]")) {
        	log("Invalid query. Please enter the number of one of the possible operations");
        	return;
        }
		int query = Integer.parseInt(input);
		
		if(query == 1) { // insert node
			if(dataNum == 0) // if there are no inserted nodes yet then, then we insert the current node
				insert(new NodeInfo(address,numID,nameID));
			else
				log("Already Inserted");
		}else if (query == 2){ // insert transaction
			log("Enter prev of transaction");
			String prev = get();
			log("Enter cont of transaction");
			String cont = get();
			Transaction t = new Transaction(prev,numID,cont);
			t.setAddress(address);
			transactions.add(t);
			insert(t);
		}else if (query == 3){ // insert block
			log("Enter prev of block");
			String prev = get();
			Block b = new Block(address,prev);
			blocks.add(b);
			insert(b);
		}else if (query == 4) {// search by name ID
			log("Please Enter the name ID to be searched");
			String name = get();
			while(!name.matches("[0-1]+")) {//Makes sure the name is a binary string
				log("Name ID should be a binary string. Please enter a valid Name ID:");
				name = get();
			}
			NodeInfo result = null;
			try{
				result = searchByNameID(name);
			}catch(RemoteException e) {
				e.printStackTrace();
				log("Remote Exception in query.");
			}
			log("The result of search by name ID is: " + result.getAddress());
		}else if(query == 5) { // search by num ID
			log("Please Enter the numeric ID to be searched");
			String numInput = get();
			while(!numInput.matches("0|[1-9][0-9]*")) {
				log("Invalid number entered. Please enter a valid number");
				numInput = get();
			}
			int num = Integer.parseInt(numInput);
			NodeInfo result = searchByNumID(num);
			log("The result of search by numberic ID is: "+ result.getAddress());
		}else if(query == 6) {
			log("Which transaction to validate, you have "+transactions.size()+ " option");
			int num = Integer.parseInt(get());
			try {
				validate(transactions.get(num));
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}else if(query == 7) { // print the lookup table of the current node
			log("In case you want the lookup table of the original node enter 0.");
			log("Otherwise, enter the index of the data node ");
			int num = Integer.parseInt(get());
			if(num < dataNum)
				printLookup(num);
			else
				log("Data node with given index does not exist");
		}
    }
	
	public void validate(Transaction t) throws RemoteException {
		ArrayList<NodeInfo> validators = getValidators(t);
		ArrayList<String> sigma = new ArrayList<>();
		String mySign = digitalSignature.signString(t.getH());
		sigma.add(mySign);
		t.setSigma(sigma);
		for(int i=0 ; i<validators.size(); ++i) {
			RMIInterface node = getRMI(validators.get(i).getAddress());
			String signature = node.PoV(t,getPublicKey());
			if(signature == null) {
				log("Validating Transaction failed.");
				return;
			}
			sigma.add(signature);
		}
		t.setSigma(sigma);
		log("Validation Successful");
	}
	/*
	 * Checks the soundness, correctness and authenticity of a transaction using other methods.
	 * Checks if the owner of the transaction has enough balance to cover validation fees.
	 * returns null in case any of the tests fails, otherwise it signs the hashvalue of the transaction
	 * and sends the signed value to the owner.
	 */
	public String PoV(Transaction t, PublicKey ownerPublicKey) {
		boolean val = isAuthenticated(t,ownerPublicKey) && isCorrect(t) && isSound(t) && hasBalanceCompliance(t);
		if(val == false)
			return null;
		String signedHash = digitalSignature.signString(t.getH());
		return signedHash;
	}
	
	/*
	 * This method validated the soundness of a given transaction
	 * It checks if the given transaction is associated with a block
	 * which does not precede the block that contains the transaction's owner's 
	 * last transaction.
	 */
	public boolean isSound(Transaction t) {
		long start = System.currentTimeMillis();
		// get the hash value of the block that contains the owner's latest transaction
		String lastblk = view.get(t.getOwner());
		// get the index of the block that the given transaction is linked to
		int tIdx = blkIdx.get(t.getPrev());
		// get the index of the block the contains the owner's latest transaction
		int bIdx = blkIdx.get(lastblk);
		long end = System.currentTimeMillis();
		log("Time of Checking Soundness: " + (end-start));
		return tIdx > bIdx ;
	}
	/*
	 * Returns true if both nodes are of same type (HONEST,HONEST) or (MALICIOUS,MALICIOUS)
	 * and returns false if both are of different types.
	 */
	public boolean isCorrect(Transaction t) {
		int ownerMode = viewMode.get(t.getOwner());
		return ownerMode == mode;
	}
	/*
	 * This method receives a transaction and the public key of the owner and verifies two things:
	 * 1- The hashvalue of the transaction was generated according to the correct equation
	 * 2- The sigma of the transaction contains the transaction's owner signed value of the hashvalue
	 */
	public boolean isAuthenticated(Transaction t, PublicKey ownerPublicKey) {
		// generate the hash using the equation to check if it was generated correctly
		String hash = hasher.getHash(t.getPrev()+t.getOwner()+t.getCont(),TRUNC);
		// return false if it was not generated properly
		if(!hash.equals(t.getH()))
			return false;
		// now get the sigma array from transaction and iterate over signatures it contains
		ArrayList<String> tSigma = t.getSigma();
		boolean verified = false;
		for(int i=0 ; i<tSigma.size(); ++i) {
			// if we find one signature which belongs to the owner then we set verified to true
			boolean is = digitalSignature.verifyString(hash, tSigma.get(i), ownerPublicKey);
			if(is)
				verified = true;
		}
		return verified;
	}
	/*
	 * Checks if the owner of the transaction has enough balance to cover the fees
	 * of validation
	 */
	public boolean hasBalanceCompliance(Transaction t) {
		int ownerBalance = viewBalance.get(t.getOwner());
		return ownerBalance >= VALIDATION_FEES;
	}
	/*
	 * This method returns the validators of a given transaction
	 */
	public ArrayList<NodeInfo> getValidators(Transaction t){
		// stores the validators to be returned
		ArrayList<NodeInfo> validators = new ArrayList<>(); 
		// used as a lookup to check if a node has been already added to the validators array
		// because the search might return the same node more than once
		// so in order to avoid this case, when we find an already added node, 
		// we repeat the search
		HashMap<String,Integer> taken = new HashMap<>(); 
		
		int count = 0 , i = 0;
		while(count < VAL_THRESHOLD) { // terminates when we get the required number of validators
			String hash = t.getPrev() + t.getOwner() + t.getCont() + i ;
			int num = Integer.parseInt(hasher.getHash(hash,TRUNC),2);
			NodeInfo node = searchByNumID(num);
			i++;
			if(taken.containsKey(node.getAddress()) || node.equals(data.get(0)))continue;
			count++;
			taken.put(node.getAddress(), 1);
			validators.add(node);
		}
		return validators;
	}
	/*
	 * This method is a helper method for insert() method
	 * It is used to make the insert() operation recursive per level.
	 * It receives the level of insertion and the direction of search
	 * if direction == 1, then the search is to the right, and the method returns the right neighbor
	 * if direction != 1, then the search is to the left, and the method returns the left neighbor
	 * This method is also directly accessed by other nodes using RMI if a search should pass through it
	 * @see RMIInterface#insertSearch(int, int, int, java.lang.String)
	 */
	public NodeInfo insertSearch(int level, int direction,int num, String target) throws RemoteException {
		
		int dataIdx = dataID.get(num);
		// If the current node and the inserted node have common bits more than the current level,
		// then this node is the neighbor so we return it
		if(commonBits(target) > level) 
			return data.get(dataIdx);
		// If search is to the right then delegate the search to right neighbor if it exists
		// If the right neighbor is null then at this level the right neighbor of the inserted node is null
		if(direction == 1) {
			if(lookup[level][1][dataIdx] == null)
				return null;
			RMIInterface rRMI = getRMI(lookup[level][1][dataIdx].getAddress());
			return rRMI.insertSearch(level,direction,lookup[level][1][dataIdx].getNumID(),target);
		} else {
			// If search is to the left then delegate the search to the left neighbor if it exists
			// If the left neighbor is null, then the left neighbor of the inserted node at this level is null.
			if(lookup[level][0][dataIdx] == null)
				return null;
			RMIInterface lRMI = getRMI(lookup[level][0][dataIdx].getAddress());
			return lRMI.insertSearch(level,direction,lookup[level][0][dataIdx].getNumID(),target);
		}
	}
	
	/*
	 * This method inserts either the current node to the skip graph of the introducer,
	 * or it is used to insert a data node.
	 */
	public void insert(NodeInfo node){
		try {
			String left = null;
			String right = null;

			// We search through the introducer node to find the node with 
			// the closest num ID
			NodeInfo position ;
			if(introducer.equalsIgnoreCase("none")) {
				position = searchByNumID(node.getNumID());
			}else {
				RMIInterface introRMI = getRMI(introducer);		
				position = introRMI.searchByNumID(node.getNumID());
			}
			
			if(position == null) {
				log("The address resulting from the search is null");
				log("Please check the introducer's IP address and try again.");
				return;
			}
			else
				log("The address resulting from the search is: " + position.getAddress());
			
			RMIInterface posRMI = getRMI(position.getAddress());
			if(posRMI == null) {
				log("RMI registry lookup at address: "+position+" failed. Insert operation stopped.");
				return;
			}
			
			// First, we insert the node at level 0
			
			int posNum = position.getNumID(); // numID of the closest node
			String posName = position.getNameID(); // nameID of the closest node
			int leftNum = -1 ; // numID of left node
			int rightNum = -1 ; // numID of right node
			
			if(posNum > node.getNumID()) { // if the closest node is to the right
				
				right = position.getAddress();
				left = posRMI.getLeftNode(0,posNum); // the left of my right will be my left
				rightNum = position.getNumID(); // we need the numID to be able to access it
				
				if(left != null) { // insert the current node in the lookup table of my left node if it exists
					RMIInterface leftRMI = getRMI(left);
					leftNum = posRMI.getLeftNumID(0,posNum);
					lookup[0][0][dataNum] = new NodeInfo(left,leftNum,posRMI.getLeftNameID(0,posNum));
					leftRMI.setRightNode(0, node,leftNum);
				}
				
				lookup[0][1][dataNum] = new NodeInfo(right,posNum,posName);
				posRMI.setLeftNode(0, node, posNum); // insert the current node in the lookup table of its right neighbor
			
			}else{ // if the closest node is to the left
				
				right  = posRMI.getRightNode(0,posNum); // the right of my left is my right
				left = position.getAddress() ; 
				leftNum = position.getNumID(); // we need the numID to be able to access it
				
				if(right != null) { // insert current node in the lookup table of its right neighbor if it exists
					RMIInterface rightRMI = getRMI(right);
					rightNum = posRMI.getRightNumID(0, posNum);
					lookup[0][1][dataNum] = new NodeInfo(right,rightNum,posRMI.getRightNameID(0,posNum)) ;
					rightRMI.setLeftNode(0,node,rightNum);
				}
				
				lookup[0][0][dataNum] = new NodeInfo(left,posNum,posName);
				posRMI.setRightNode(0, node,posNum);
				
			}
			
			// Now, we insert the node in the rest of the levels
			// In level i , we make a recursive search for the nodes that will be
			// the neighbors of the inserted nodes at level i+1
			
			int level = 0;
			while(level < maxLevels) {
				
				if(left != null) {
					
					RMIInterface leftRMI = getRMI(left);
					NodeInfo lft = leftRMI.insertSearch(level,-1,leftNum,node.getNameID()); // start search left
					lookup[level+1][0][dataNum] = lft ; 
					
					// set left and leftNum to default values (null,-1)
					// so that if the left neighbor is null then we no longer need
					// to search in higher levels to the left
					left = null;
					leftNum = -1;
					
					if(lft != null) {
						RMIInterface lftRMI = getRMI(lft.getAddress());
						lftRMI.setRightNode(level+1,node, lft.getNumID());
						left = lft.getAddress();
						leftNum = lft.getNumID();
					}	
				}
				if(right != null) {
					
					RMIInterface rightRMI = getRMI(right);
					NodeInfo rit = rightRMI.insertSearch(level, 1, rightNum, node.getNameID()); // start search right
					lookup[level+1][1][dataNum] = rit;
					
					// set right and rightNum to default values (null,-1)
					// so that if the right neighbor is null then we no longer need
					// to search in higher levels to the right
					right = null;
					rightNum = -1;
					
					if(rit != null) {
						RMIInterface ritRMI = getRMI(rit.getAddress());
						ritRMI.setLeftNode(level+1, node, rit.getNumID());
						right = rit.getAddress();
						rightNum = rit.getNumID();
					}
				}
				level++;
			}
			// after we conclude inserting the node in all levels,
			// we add the inserted node to the data array
			// and we map its numID with its index in the data array using dataID
			dataID.put(node.getNumID(),dataNum);
			data.add(node);
			dataNum++;
		}catch(RemoteException e) {
			e.printStackTrace();
			log("Remote Exception thrown in insert function.");
		}
	}
	
	
	
	/*
	 * This method receives a number, and returns the data node (or possibly the main node)
	 * which has the closest numID to the given number
	 */
	public int getBestNum(int num) {
		int dif = Math.abs(num-data.get(0).getNumID());
		int best = 0 ;
		for(int i=1 ; i<dataNum ; ++i)
			if(Math.abs(data.get(i).getNumID()-num) < dif) {
				dif = Math.abs(data.get(i).getNumID()-num);
				best = i;
			}
		return best;
	}
	
	/*
	 * This method is a hepler method for searchByNumID()
	 * It recieves the target numID and the level it is searching in,
	 * and it routes the search through the skip graph recursively using RMI
	 * @see RMIInterface#searchNum(int, int)
	 */
	public NodeInfo searchNum(int targetInt,int level){
		
		log("Search at: "+address); // we use this to see when a search has passed through this node
		
		int dataIdx = getBestNum(targetInt);// get the data node (or main node) that is closest to the target search
		int num = data.get(dataIdx).getNumID();
		if(num == targetInt) 
			return data.get(dataIdx) ;
		
		// If the target is greater than the current node then we should search right
		if(num < targetInt) {
			
			// Keep going down levels as long as there is either no right neighbor
			// or the right neighbor has a numID greater than the target
			while(level >= 0 && (lookup[level][1][dataIdx] == null || lookup[level][1][dataIdx].getNumID() > targetInt))
				level--;
			// If there are no more levels to go down to return the current node
			if(level < 0) 			
				return data.get(dataIdx);
			// delegate the search to the right neighbor
			RMIInterface rightRMI = getRMI(lookup[level][1][dataIdx].getAddress());
			try{
				return rightRMI.searchNum(targetInt,level);
			}catch(Exception e) {
				log("Exception in searchNum. Target: "+targetInt);
			}
		}
		else { // If the target is less than the current node then we should search left 
			// Keep going down levels as long as there is either no right neighbor
			// or the left neighbor has a numID greater than the target
			while(level >= 0 && (lookup[level][0][dataIdx] == null || lookup[level][0][dataIdx].getNumID() < targetInt))
				level--;
			// If there are no more levels to go down to return the current node
			if(level < 0)
				return data.get(dataIdx);
			// delegate the search to the left neighbor
			RMIInterface leftRMI = getRMI(lookup[level][0][dataIdx].getAddress());
			try{
				return leftRMI.searchNum(targetInt, level);
			}catch(Exception e) {
				log("Exception in searchNum. Target: "+targetInt);
			}
		}
		return data.get(dataIdx);
	}
	
	/* 
	 * Executes a search through the skip graph by numeric id and returns the a NodeInfo object 
	 * which contains the address, numID, and nameID of the node with closest numID to the target
	 * It starts the search from last level of the current node
	 * @see RMIInterface#searchByNumID(java.lang.String)
	 */
	public NodeInfo searchByNumID(int searchTarget){
	
		int level = maxLevels;
		int dataIdx = getBestNum(searchTarget); // route search to closest data node
		if(lookup[0][0][dataIdx] == null && lookup[0][1][dataIdx] == null)
			return data.get(dataIdx);
		return searchNum(searchTarget,level);	
	}
	
	/*
	 * This method receives a nameID and returns the index of the data node which has
	 * the most common prefix with the given nameID
	 */
	public int getBestName(String name) {
		int best = 0;
		int val = commonBits(name,data.get(0).getNameID());
		for(int i=1 ; i<dataNum ; ++i) {
			int bits = commonBits(name,data.get(i).getNameID());
			if(bits > val) {
				val = bits;
				best = i;
			}
		}
		return best;
	}
	
	/*
	 * This method is a helper method for searchByNameID()
	 * It receives the target nameID and the level it is searching in, and also the direction
	 * of search, and it routes the search through the skip graph recursively using RMI
	 * It return the most similar node if the node itself is not found
	 * The similarity is defined to be the the maximum number of common bits
	 * If direction == 1, then search is to the right 
	 * If direction == 0, then search is to the left
	 * @see RMIInterface#searchName(java.lang.String, int, int)
	 */
	public NodeInfo searchName(String searchTarget,int level,int direction) throws RemoteException {
		
		int dataIdx = getBestName(searchTarget);
		if(data.get(dataIdx).getNameID().equals(searchTarget)) // if the current node hold the same nameID, return it.
			return data.get(dataIdx);
		// calculate common bits to find to which level the search must be routed
		int newLevel = commonBits(searchTarget); 
		
		// If the number of common bits is not more than the current level
		// then we continue the search in the same level in the same direction
		if(newLevel <= level ) {
			if(lookup[level][direction][dataIdx] == null)// If no more nodes in this direction return the current node
				return data.get(dataIdx) ;
			RMIInterface rightRMI = getRMI(lookup[level][direction][dataIdx].getAddress());
			return rightRMI.searchName(searchTarget, level, direction);
		}
		// If the number of common bits is more than the current level
		// then the search will be continued on the new level
		// so we start a search in both directions in the new level
		
		NodeInfo result = data.get(dataIdx); // we initialize the result to current node
		
		// First we start the search on the same given direction and wait for the result it returns
		if(lookup[newLevel][direction][dataIdx] != null) {
			RMIInterface rightRMI = getRMI(lookup[newLevel][direction][dataIdx].getAddress());
			result = rightRMI.searchName(searchTarget,newLevel,direction);
		}
		// If it returns a result that differs from the current node then we check it
		if(result != null && !result.equals(data.get(dataIdx))) {
			RMIInterface resultRMI = getRMI(result.getAddress());
			// If this is the result we want return it, otherwise continue the search in the opposite direction
			if(resultRMI.getNameID().contains(searchTarget))
				return result;
		}
		// Continue the search on the opposite direction
		if(lookup[newLevel][1-direction] != null) {
			RMIInterface leftRMI = getRMI(lookup[newLevel][1-direction][dataIdx].getAddress());
			NodeInfo k = leftRMI.searchName(searchTarget, newLevel, 1-direction);
			if(result == null || commonBits(k.getNameID(),data.get(dataIdx).getNameID()) > commonBits(result.getNameID(),data.get(dataIdx).getNameID()))
				result = k;
		}
		return result;			
	}
	
	/*
	 * This methods starts a search by nameID, and returns the node as an instance
	 * of NodeInfo class which contains (address, numID, nameID) of the node,
	 * such that the nameID of the returned node is the most similar with the searchTarget.
	 * Similarity is defined to be the maximum number of common bits between the two strings 
	 * @see RMIInterface#searchByNameID(java.lang.String)
	 */
	public NodeInfo searchByNameID(String searchTarget) throws RemoteException{
		
		int dataIdx = getBestName(searchTarget);
		int newLevel = commonBits(searchTarget);
		NodeInfo result = data.get(dataIdx);
		
		// First execute the search in the right direction and see the result it returns
		if(lookup[newLevel][1][dataIdx] != null) {
			RMIInterface rightRMI = getRMI(lookup[newLevel][1][dataIdx].getAddress());
			result = rightRMI.searchName(searchTarget,newLevel,1);
		}
		// If the result is not null and is different from the default value we check it
		if(result != null && !result.equals(data.get(dataIdx))) {
			RMIInterface resultRMI = getRMI(result.getAddress());
			// If this is the result we want return it, otherwise continue searching to the left
			if(resultRMI.getNameID().contains(searchTarget))
				return result;
		}
		// If the desired result was not found try to search to the left
		if(lookup[newLevel][0][dataIdx] != null) {
			RMIInterface leftRMI = getRMI(lookup[newLevel][0][dataIdx].getAddress());
			NodeInfo k = leftRMI.searchName(searchTarget, newLevel, 0);
			if(commonBits(k.getNameID(),data.get(dataIdx).getNameID()) > commonBits(result.getNameID(),data.get(dataIdx).getNameID()))
				result = k;
		}
		return result;
	}
	/*
	 * getters and setters for lookup table and numID and nameID
	 * 
	 */
	public String getLeftNode(int level,int num) throws RemoteException {
		if(lookup[level][0][dataID.get(num)] == null)
			return null;
		return lookup[level][0][dataID.get(num)].getAddress();
	}
	
	public String getRightNode(int level, int num) throws RemoteException {
		if(lookup[level][1][dataID.get(num)] == null)
			return null;
		return lookup[level][1][dataID.get(num)].getAddress();
	}
	public void setLeftNode(int level,NodeInfo newNode, int num) throws RemoteException{
		log("LeftNode at level "+level+" set to: "+newNode.getAddress());
		lookup[level][0][dataID.get(num)] = newNode;
	}
	public void setRightNode(int level,NodeInfo newNode, int num) throws RemoteException {
		log("RightNode at level "+level+" set to: "+newNode.getAddress());
		lookup[level][1][dataID.get(num)] = newNode ;
	}
	
	public int getNumID(){
		return numID;
	}
	public String getNameID() {
		return nameID ;
	}
	
	public int getLeftNumID(int level,int num) {
		return lookup[level][0][dataID.get(num)].getNumID();
	}
	public int getRightNumID(int level, int num) {
		return lookup[level][1][dataID.get(num)].getNumID();
	}
	public String getLeftNameID(int level,int num) {
		return lookup[level][0][dataID.get(num)].getNameID();
	}
	public String getRightNameID(int level,int num) {
		return lookup[level][1][dataID.get(num)].getNameID();
	}
	
	/*
	 * This method returns an RMI instance of the node with the given address
	 */
	public static RMIInterface getRMI(String adrs) {		
		if(validateIP(adrs))
			try {
				return (RMIInterface)Naming.lookup("//"+adrs+"/RMIImpl");
			}catch(Exception e) {
				log("Exception while attempting to lookup RMI located at address: "+adrs);
			}
		else {
			log("Error in looking up RMI. Address: "+ adrs + " is not a valid address.");
		}
		return null;
	}
	
	/*
	 * This method validates the ip and makes sure its of the form xxx.xxx.xxx.xxx
	 */
	private static boolean validateIP(String adrs) { 
		int colonIndex = adrs.indexOf(':');
		String ip = adrs;
		if(colonIndex != -1) ip = adrs.substring(0,colonIndex);
		String[] parts = ip.split("\\.");
		if(parts.length!=4) {
			return false;
		}
		try {
			for(String el : parts) {
				int num = Integer.parseInt(el);
				if(num<0||num>255) return false;
			}
		}catch(NumberFormatException e) {
			return false;
		}
		if(ip.endsWith("."))
			return false;
		return true;
	}
	
	// This method calculate the length of the common prefix 
	// between the nameID of the current node and the given name
	public static int commonBits(String name) {	
		if(name.length() != nameID.length())
			return -1;
		int i = 0 ;
		for(i = 0 ; i < name.length() && name.charAt(i) == nameID.charAt(i);i++);
		log("Common Prefix for " + nameID + " and " + name + " is: " + i);
		return i ;		
	}
	
	public int getBalance() {
		return balance;
	}
	
	public PublicKey getPublicKey() {
		return digitalSignature.getPublicKey();
	}
	/*
	 * This method returns the length of the common prefix between two given strings
	 */
	public static int commonBits(String name1, String name2) {
		if(name1 == null || name2 == null) {
			return -1;
		}
		if(name1.length() != name2.length())
			return -1;
		int i = 0;
		for(i = 0; i < name1.length() && name1.charAt(i) == name2.charAt(i) ; ++i);
		log("Common Prefix for " + name1 + " and " + name2 + " is: " + i);
			return i;		
		}
	/*
	 * A shortcut for printing to console
	 */
	public static void log(String s) {
		System.out.println(s);
	}
	public static void logLine(String s) {
		System.out.print(s);
	}
	/* 
	 * Print the contents of the lookup table
	 */
	public static void printLookup(int num) {
        System.out.println("\n");
        for(int i = maxLevels-1 ; i >= 0 ; i--)
        {
            for(int j = 0 ; j<2 ; j++)
            	if(lookup[i][j][num] == null)
            		logLine("null\t");
            	else
            		logLine(lookup[i][j][num].getAddress()+"\t");
            log("\n\n");
        }
    }
	/*
	 * A shortcut for getting input from user
	 */
	public static String get() {
		String response = in.nextLine();
		return response;
	}
	
	
	/*
	 * Getters (For use in the remote testing)
	 */

	public ArrayList<Transaction> getTransactions() throws RemoteException{
		return transactions;
	}
	
	public int getDataNum() throws RemoteException{
		return dataNum;
	}
	
	public ArrayList<NodeInfo> getData() throws RemoteException{
		return data;
	}
	public NodeInfo[][][] getLookupTable() throws RemoteException{
		return lookup;
	}
	
	public void put(Transaction t) throws RemoteException{
		transactions.add(t);
		insert(t);
	}
	public void put(Block t) throws RemoteException{
		blocks.add(t);
		insert(t);
	}

}
