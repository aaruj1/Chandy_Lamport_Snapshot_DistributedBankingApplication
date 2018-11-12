import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author aaruj1
 *
 */
public class RequestHandler {

	private boolean acceptFlag;
	private List<Integer> incomingTransferList;
	private Bank.InitBranch.Branch branch;

	private static Lock mutexLock = new ReentrantLock();

	public RequestHandler() {

	}

	public RequestHandler(Bank.InitBranch.Branch branchI) {
		incomingTransferList = new ArrayList<>();
		setAcceptFlag(true);
		branch = branchI;
	}

	public void initiateTransferSequences(Branch branch, RequestHandler requestHandler) {
//		System.out.println("---------------- Inside TransferMoney --------------");
		while (true) {
			try {
				Random randomTime = new Random();
				int randomNumber = randomTime.nextInt(branch.getTimeInterval());
				Thread.sleep(randomNumber);

				mutexLock.lock();

				int balance = branch.getBalance();

				int amountToTransfer = (balance * requestHandler.getRandomNumber()) / 100;

				if (balance - amountToTransfer > 0 && (branch.getBranchesMap() != null && branch.getBranchesMap().size() > 0)) {

					String randomBranchName = requestHandler.getRandomBranch(branch.getBranchesMap());

					if (!branch.getCurrentBranchName().equalsIgnoreCase(randomBranchName)) {
//						System.out.println("--------------------- Transfer started -----------------------");

						String branchIPAddress = branch.getBranchesMap().get(randomBranchName).getIp();
						int branchPort = branch.getBranchesMap().get(randomBranchName).getPort();

						Bank.Transfer.Builder transferBuilder = Bank.Transfer.newBuilder();
						transferBuilder.setSrcBranch(branch.getCurrentBranchName());
						transferBuilder.setDstBranch(randomBranchName);
						transferBuilder.setMoney(amountToTransfer);
						transferBuilder.build();

						Bank.BranchMessage branchMessage = Bank.BranchMessage.newBuilder().setTransfer(transferBuilder).build();

//						System.out.println("BranchMessage Object :: \n" + branchMessage.toString());
						try {
							branch.withdrawBalance(amountToTransfer);
							balance = branch.getBalance();

//							System.out.println("Transfer amount : " + amountToTransfer + " to " + randomBranchName);
//							System.out.println("Remaining balance of " + branch.getCurrentBranchName() + " after sending : " + balance);

							Socket socket = new Socket(branchIPAddress, branchPort);

							branchMessage.writeDelimitedTo(socket.getOutputStream());
							socket.getOutputStream().write(branch.getCurrentBranchName().getBytes());
							socket.close();

//							System.out.println("--------------------- Transfer End -----------------------");

						} catch (IOException e) {
							System.err.println("Exception occured while transfering money." + e.getMessage());
							e.printStackTrace();
						}
					}
//					else {
//						System.out.println("Trying to send money to itself.");
//					}
				}
//				else {
//					System.out.println("Amount is not sufficient");
//				}
				mutexLock.unlock();

			} catch (InterruptedException e) {
				System.err.println("Exception while transfering money." + e.getMessage());
				e.printStackTrace();
			} catch (Exception e) {
				System.err.println(e.getMessage());
				e.printStackTrace();
			}
//			count++;
//			if (count > 20)
//				break;
		}
	}

	public void handleReceive(InputStream inputStream, Bank.BranchMessage branchMessage, Branch branch, RequestHandler requestHandler) {
		// Transfer
//		System.out.println("------------- Start Receiving Money -------------");

		try {
			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
			String branchName = bufferedReader.readLine();

			Bank.Transfer transfer = branchMessage.getTransfer();

			int amount = transfer.getMoney();

//			String sourceBranch = transfer.getSrcBranch();
//			String destinationBranch = transfer.getDstBranch();

//			System.out.println("Receiving amount " + amount + " from sourceBranch :: " + sourceBranch + " to destinationBranch :: "
//					+ destinationBranch);
			mutexLock.lock();
			branch.addBalance(amount);
			int balance = branch.getBalance();
//			System.out.println(" New Balance of " + branch.getCurrentBranchName() + " after receive = " + balance);
			createLocalSnapshot(branchName, amount, branch);
			mutexLock.unlock();
//			System.out.println("------------- End Receiving Money -------------");

		} catch (IOException ioe) {
			System.err.println(ioe.getMessage());
			ioe.printStackTrace();
		} catch (Exception ex) {
			System.err.println(ex.getMessage());
			ex.printStackTrace();
		}
	}

	private static void createLocalSnapshot(String branchName, int amount, Branch branch) {
		RequestHandler requestHandler = null;
		if (branch.getChannelLocalSnapshotMap() != null) {
			for (int snapshotId : branch.getChannelLocalSnapshotMap().keySet()) {
				Map<String, RequestHandler> requestHandlerMap = branch.getChannelLocalSnapshotMap().get(snapshotId);
				requestHandler = requestHandlerMap.get(branchName);
				requestHandler.add(amount);
			}
		}

	}

	public void initiateSnapshot(Bank.BranchMessage branchMessage, Branch branch) {
//		System.out.println("------------- Start initiateSnapshot -------------");

		Bank.InitSnapshot intiSnapShot = branchMessage.getInitSnapshot();
		int snapshotId = intiSnapShot.getSnapshotId();
//		System.out.println("SnapShot received : " + snapshotId);

		try {
			if (branch.getLocalSnapshotConcurrentMap() != null && !branch.getLocalSnapshotConcurrentMap().containsKey(snapshotId)) {
				mutexLock.lock();

				Bank.ReturnSnapshot.LocalSnapshot.Builder localSnapshot = Bank.ReturnSnapshot.LocalSnapshot.newBuilder();
				localSnapshot.setSnapshotId(snapshotId);
				localSnapshot.setBalance(branch.getBalance());

				Bank.ReturnSnapshot.LocalSnapshot localSnapshotObj = localSnapshot.build();
//				System.out.println("localSnapshotObj :: " + localSnapshotObj);

				branch.getLocalSnapshotConcurrentMap().put(snapshotId, localSnapshotObj);

				if (!branch.getChannelLocalSnapshotMap().containsKey(snapshotId)) {

					Map<String, RequestHandler> requestHandlerMap = new HashMap<String, RequestHandler>();

					for (Bank.InitBranch.Branch initBranchObj : branch.getBranchesMap().values()) {
						if (!initBranchObj.getName().equalsIgnoreCase(branch.getCurrentBranchName()))
							requestHandlerMap.put(initBranchObj.getName(), new RequestHandler(initBranchObj));
					}
					branch.getChannelLocalSnapshotMap().put(snapshotId, requestHandlerMap);
				} else {
//					System.out.println("Already recorded the snapshot : " + snapshotId);
				}

//				System.out.println("Localsnapshot recorded in initiateSnapshot for the branch : " + branch.getCurrentBranchName() + " is "
//						+ branch.getLocalSnapshotConcurrentMap().get(snapshotId));
			}

			sendMarkers(snapshotId, branch);
			mutexLock.unlock();
//			System.out.println("------------- End initiateSnapshot -------------");

		} catch (Exception ex) {
			System.err.println(ex.getMessage());
			ex.printStackTrace();
		}

	}

	public void receiveMarkers(Bank.BranchMessage branchMessage, InputStream inputStream, Branch branch) {

//		System.out.println("------------- Start receiveMarkers  -------------");
		try {

			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
			String branchName;
			branchName = bufferedReader.readLine();

			Bank.Marker marker = branchMessage.getMarker();
			int snapshotId = marker.getSnapshotId();
//			System.out.println("Marker snapshot id " + snapshotId + " received");

			if (branch.getLocalSnapshotConcurrentMap() != null && !branch.getLocalSnapshotConcurrentMap().containsKey(snapshotId)) {
				mutexLock.lock();
				Bank.ReturnSnapshot.LocalSnapshot.Builder localSnapshot = Bank.ReturnSnapshot.LocalSnapshot.newBuilder();
				localSnapshot.setBalance(branch.getBalance());
				localSnapshot.setSnapshotId(snapshotId);

				Bank.ReturnSnapshot.LocalSnapshot localSnapShotObj = localSnapshot.build();

				branch.getLocalSnapshotConcurrentMap().put(snapshotId, localSnapShotObj);

				if (!branch.getChannelLocalSnapshotMap().containsKey(snapshotId)) {
					Map<String, RequestHandler> requestHandlerMap = new HashMap<String, RequestHandler>();

					for (Bank.InitBranch.Branch initBranchObj : branch.getBranchesMap().values()) {

						if (!initBranchObj.getName().equalsIgnoreCase(branch.getCurrentBranchName())) {
							requestHandlerMap.put(initBranchObj.getName(), new RequestHandler(initBranchObj));

							if (branchName.equalsIgnoreCase(initBranchObj.getName())) {
								requestHandlerMap.get(branchName).setAcceptFlag(false);
							}
						}
					}
					branch.getChannelLocalSnapshotMap().put(snapshotId, requestHandlerMap);

				} else {
//					System.out.println("ChannelLocalSnapshotMap contains the snapshotId " + snapshotId);
				}
//				System.out.println("Marker recorded in receiveMarkers for the branch : " + branchName + " is "
//						+ branch.getLocalSnapshotConcurrentMap().get(snapshotId));

				if (getMarkerFlag(snapshotId, branch)) {
					sendMarkers(snapshotId, branch);
				}
				mutexLock.unlock();
			} else {
				if (branch.getChannelLocalSnapshotMap() != null) {
					mutexLock.lock();
					Map<String, RequestHandler> requestHandlerMap = branch.getChannelLocalSnapshotMap().get(snapshotId);
					RequestHandler requestHandler = requestHandlerMap.get(branchName);
					requestHandler.setAcceptFlag(false);
					mutexLock.unlock();
				}

			}

		} catch (IOException ioe) {
			System.err.println(ioe);
			ioe.printStackTrace();
		}
//		System.out.println("------------- End receiveMarkers  -------------");

	}

	private synchronized static void sendMarkers(int snapshotId, Branch branch) {
//		System.out.println("------------- Start sendMarkers -------------");
		for (String branchName : branch.getBranchesMap().keySet()) {
			try {
//				Thread.sleep(100);
				Bank.Marker.Builder marker = Bank.Marker.newBuilder();
				marker.setSnapshotId(snapshotId);
				marker.setSrcBranch(branch.getCurrentBranchName());
				marker.setDstBranch(branchName);
				marker.build();

				Bank.BranchMessage branchMessage = Bank.BranchMessage.newBuilder().setMarker(marker).build();
				Socket socket = null;
				if (!branchName.equalsIgnoreCase(branch.getCurrentBranchName())) {
					Bank.InitBranch.Branch initBranchObj = branch.getBranchesMap().get(branchName);
//					System.out.println("Marker message sending to branch :  " + initBranchObj.getName());
					socket = new Socket(initBranchObj.getIp(), initBranchObj.getPort());
					branchMessage.writeDelimitedTo(socket.getOutputStream());
					socket.getOutputStream().write(branch.getCurrentBranchName().getBytes());
					if (socket != null) {
						socket.close();
					}
				}
//				System.out.println("------------- End sendMarkers -------------");
			} catch (IOException ioe) {
				System.err.println(ioe);
				ioe.printStackTrace();
			}
		}
	}

	private synchronized static boolean getMarkerFlag(int snapShotId, Branch branch) {
		Map<String, RequestHandler> requestHandlerMap = branch.getChannelLocalSnapshotMap().get(snapShotId);
		boolean flag = false;
		for (RequestHandler channel : requestHandlerMap.values()) {
			flag = flag || channel.isAcceptFlag();
		}
		return flag;
	}

	public void retrieveSnapshot(Socket socket, Bank.BranchMessage branchMessage, InputStream inputStream, Branch branch) {

//		System.out.println("------------- Start retrieveSnapshot  -------------");
		try {
			int retrieveSnapshotId = branchMessage.getRetrieveSnapshot().getSnapshotId();
//			System.out.println("RetrieveSnapshot id = " + retrieveSnapshotId);

			if ((branch.getLocalSnapshotConcurrentMap() != null && branch.getLocalSnapshotConcurrentMap().containsKey(retrieveSnapshotId))
					&& (branch.getChannelLocalSnapshotMap() != null
							&& branch.getChannelLocalSnapshotMap().containsKey(retrieveSnapshotId))) {

				Bank.ReturnSnapshot.LocalSnapshot.Builder returnLocalSnapShotBuilder = Bank.ReturnSnapshot.LocalSnapshot.newBuilder();

				int localSnapShotBalance = branch.getLocalSnapshotConcurrentMap().get(retrieveSnapshotId).getBalance();

				returnLocalSnapShotBuilder.setBalance(localSnapShotBalance);
				returnLocalSnapShotBuilder.setSnapshotId(retrieveSnapshotId);

				mutexLock.lock();

				Map<String, RequestHandler> requestHandlerMap = branch.getChannelLocalSnapshotMap().get(retrieveSnapshotId);

				ConcurrentHashMap<String, RequestHandler> concurrentHashMap = new ConcurrentHashMap<String, RequestHandler>();
				concurrentHashMap.putAll(requestHandlerMap);

				for (String incomingBranchChannel : concurrentHashMap.keySet()) {
					RequestHandler requestHandler = concurrentHashMap.get(incomingBranchChannel);
					int sum = requestHandler.getSumOfChannel();
					returnLocalSnapShotBuilder.addChannelState(sum);
				}

				Bank.ReturnSnapshot.LocalSnapshot localSnapshot = returnLocalSnapShotBuilder.build();

				branchMessage = Bank.BranchMessage.newBuilder()
						.setReturnSnapshot(Bank.ReturnSnapshot.newBuilder().setLocalSnapshot(localSnapshot)).build();

				mutexLock.unlock();
				branchMessage.writeDelimitedTo(socket.getOutputStream());

				socket.shutdownOutput();
				socket.close();

			}
		} catch (IOException ioe) {
			System.err.println(ioe.getMessage());
			ioe.printStackTrace();
		} catch (Exception ex) {
			System.err.println(ex.getMessage());
			ex.printStackTrace();
		}

//		System.out.println("------------- End retrieveSnapshot  -------------");

	}

	/**
	 * 
	 * @param
	 * @return randomNumber
	 */

	public synchronized int getRandomNumber() {

		Random randomPercentWithdraw = new Random();
		int randomNumber = randomPercentWithdraw.nextInt(5) + 1;
		return randomNumber;
	}

	/**
	 * 
	 * @param branchesMap
	 * @return RandomBranchName
	 */

	public synchronized String getRandomBranch(Map<String, Bank.InitBranch.Branch> branchesMap) {

		Random randomBranch = new Random();
		int randomBranchNumber = randomBranch.nextInt(branchesMap.size());
		List<String> list = new ArrayList<>(branchesMap.keySet());
		String randomBranchName = list.get(randomBranchNumber);
		return randomBranchName;
	}

	public boolean isAcceptFlag() {
		return acceptFlag;
	}

	public synchronized void setAcceptFlag(boolean acceptFlag) {
		this.acceptFlag = acceptFlag;
	}

	public synchronized void add(int amount) {
		if (isAcceptFlag())
			incomingTransferList.add(amount);
	}

	public List<Integer> getincomingTransferList() {
		return incomingTransferList;
	}

	public String toString() {
		return " " + isAcceptFlag() + " " + incomingTransferList.toString() + " ";
	}

	public Bank.InitBranch.Branch getBranch() {
		return branch;
	}

	public synchronized int getSumOfChannel() {
		int sum = 0;
		for (int i : incomingTransferList)
			sum = sum + i;
		return sum;
	}

}
