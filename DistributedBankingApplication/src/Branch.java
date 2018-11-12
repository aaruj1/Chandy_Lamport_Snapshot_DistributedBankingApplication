import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author aaruj1
 *
 */
public class Branch {

	private String currentBranchName;
	private int currentBranchPort;
	private int timeInterval;

	private volatile Integer balance;

	private Map<String, Bank.InitBranch.Branch> branchesMap;
	private ConcurrentMap<Integer, Map<String, RequestHandler>> channelLocalSnapshotMap;
	private ConcurrentHashMap<Integer, Bank.ReturnSnapshot.LocalSnapshot> localSnapshotConcurrentMap;

	public static void main(String[] args) {

		if (args.length != 3) {
			System.out.println("Please provide : <branch-name> <port> and <time-interval>");
			System.exit(0);
		}

		ServerSocket serverSocket = null;
		Socket socket = null;

		try {

			String currentBranchName = args[0];
			int currentBranchPort = Integer.parseInt(args[1]);
			int timeInterval = Integer.parseInt(args[2]);

			String ipAddress = InetAddress.getLocalHost().getHostAddress();

			System.out.println("--------------- Branch Details ---------------");
			System.out.println(currentBranchName + " running on IP Address " + ipAddress + " and Port " + currentBranchPort);

			serverSocket = new ServerSocket(currentBranchPort);
			Branch branch = new Branch(currentBranchName, currentBranchPort, timeInterval);

			while (true) {
				RequestHandler requestHandler = new RequestHandler();

				socket = serverSocket.accept();
				InputStream inputStream = socket.getInputStream();

				Bank.BranchMessage branchMessage = Bank.BranchMessage.parseDelimitedFrom(inputStream);

//				System.out.println("New Message received");
//				System.out.println(branchMessage.toString());

				// InitBranch
				if (branch.getBranchesMap().isEmpty() && branchMessage.getInitBranch().getAllBranchesCount() > 0
						&& branchMessage.hasInitBranch()) {

					branch.setBalance(branchMessage.getInitBranch().getBalance());

					int balance = branch.getBalance();
//					System.out.println("Current Balance of " + currentBranchName + " : " + balance);

//					System.out.println("Total Number of Branches :: " + branchMessage.getInitBranch().getAllBranchesList().size());

//					System.out.println(
//							"Branch Object received from Cotroller : " + branchMessage.getInitBranch().getAllBranchesList().toString());

					for (int i = 0; i < branchMessage.getInitBranch().getAllBranchesCount(); i++) {
						branch.getBranchesMap().put(branchMessage.getInitBranch().getAllBranches(i).getName(),
								branchMessage.getInitBranch().getAllBranches(i));
					}
//					System.out.println("Branches Map :: \n " + branch.getBranchesMap().toString());

					Thread thread = new Thread() {
						public void run() {
							requestHandler.initiateTransferSequences(branch, requestHandler);
						}
					};
//					Thread.sleep(200);
					new Thread(thread).start();
				} else if (branchMessage.hasTransfer()) {
					// Handle receive money
//					System.out.println("Handle receive money");
					Thread.sleep(500);
					requestHandler.handleReceive(inputStream, branchMessage, branch, requestHandler);
//					Thread.sleep(200);
				} else if (branchMessage.hasInitSnapshot()) {
					// Initiate Snapshot Process
//					System.out.println("Initiate Snapshot Process");
					requestHandler.initiateSnapshot(branchMessage, branch);
//					Thread.sleep(200);
				} else if (branchMessage.hasMarker()) {
					// Receive Markers
//					System.out.println("Receive Markers");
					requestHandler.receiveMarkers(branchMessage, inputStream, branch);
//					Thread.sleep(200);
				} else if (branchMessage.hasRetrieveSnapshot()) {
					// Retrieve Snapshot
//					System.out.println("Retrieve Snapshot");
					requestHandler.retrieveSnapshot(socket, branchMessage, inputStream, branch);
//					Thread.sleep(200);
				}

			}

		} catch (IOException ioe) {
			System.err.println(ioe.getMessage());
			ioe.printStackTrace();
		} catch (InterruptedException ie) {
			System.err.println(ie);
			ie.printStackTrace();
		}

	}

	public Branch(String currentBranchName, int currentBranchPort, int timeInterval) {
		this.currentBranchName = currentBranchName;
		this.currentBranchPort = currentBranchPort;
		this.timeInterval = timeInterval;
		branchesMap = new ConcurrentHashMap<>();
		localSnapshotConcurrentMap = new ConcurrentHashMap<>();
		channelLocalSnapshotMap = new ConcurrentHashMap<>();
	}

	public synchronized int getBalance() {
		return this.balance;
	}

	public synchronized void setBalance(int balance) {
		this.balance = balance;
	}

	public synchronized void addBalance(int amount) {
		this.balance += amount;
	}

	public synchronized void withdrawBalance(int amount) {
		if (this.balance > amount) {
			this.balance -= amount;
		} else {
			System.out.println("Not Sufficient balance.");
		}
	}

	/**
	 * @return the currentBranchName
	 */
	public String getCurrentBranchName() {
		return currentBranchName;
	}

	/**
	 * @param currentBranchName the currentBranchName to set
	 */
	public void setCurrentBranchName(String currentBranchName) {
		this.currentBranchName = currentBranchName;
	}

	/**
	 * @return the currentBranchPort
	 */
	public int getCurrentBranchPort() {
		return currentBranchPort;
	}

	/**
	 * @param currentBranchPort the currentBranchPort to set
	 */
	public void setCurrentBranchPort(int currentBranchPort) {
		this.currentBranchPort = currentBranchPort;
	}

	/**
	 * @return the timeInterval
	 */
	public int getTimeInterval() {
		return timeInterval;
	}

	/**
	 * @param timeInterval the timeInterval to set
	 */
	public void setTimeInterval(int timeInterval) {
		this.timeInterval = timeInterval;
	}

	/**
	 * @return the branchesMap
	 */
	public Map<String, Bank.InitBranch.Branch> getBranchesMap() {
		return branchesMap;
	}

	/**
	 * @param branchesMap the branchesMap to set
	 */
	public void setBranchesMap(Map<String, Bank.InitBranch.Branch> branchesMap) {
		this.branchesMap = branchesMap;
	}

	/**
	 * @return the channelLocalSnapshotMap
	 */
	public ConcurrentMap<Integer, Map<String, RequestHandler>> getChannelLocalSnapshotMap() {
		return channelLocalSnapshotMap;
	}

	/**
	 * @param channelLocalSnapshotMap the channelLocalSnapshotMap to set
	 */
	public void setChannelLocalSnapshotMap(ConcurrentMap<Integer, Map<String, RequestHandler>> channelLocalSnapshotMap) {
		this.channelLocalSnapshotMap = channelLocalSnapshotMap;
	}

	/**
	 * @return the localSnapshotConcurrentMap
	 */
	public ConcurrentHashMap<Integer, Bank.ReturnSnapshot.LocalSnapshot> getLocalSnapshotConcurrentMap() {
		return localSnapshotConcurrentMap;
	}

	/**
	 * @param localSnapshotConcurrentMap the localSnapshotConcurrentMap to set
	 */
	public void setLocalSnapshotConcurrentMap(ConcurrentHashMap<Integer, Bank.ReturnSnapshot.LocalSnapshot> localSnapshotConcurrentMap) {
		this.localSnapshotConcurrentMap = localSnapshotConcurrentMap;
	}

}
