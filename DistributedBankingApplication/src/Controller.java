import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author aaruj1
 *
 */
public class Controller {

	private static Map<String, Bank.InitBranch.Branch> branchesMap;

	public static void main(String[] args) {
		try {

			if (args.length != 2) {
				System.err.println("Please provide : <total-money> and <branch-filename.txt>");
				System.exit(0);
			}

			if (args[0] != null && (Double.parseDouble(args[0]) - (int) (Double.parseDouble(args[0])) > 0)) {
				System.err.println("Initial balance must be Integer.");
				System.exit(0);
			}
			int totalMoney = Integer.parseInt(args[0]);

			String branchFileName = args[1];
			int initialBalanceOfEachBranch = 0;
			String line = null;
			Bank.InitBranch.Builder initBranch = Bank.InitBranch.newBuilder();

			File file = new File(branchFileName);
			if (!file.exists()) {
				System.err.println("File does not exist.");
				System.exit(0);
			}
			BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
			Controller controller = new Controller();
			while ((line = bufferedReader.readLine()) != null) {
				String branchName = line.split(" ")[0].trim();
				String ipAddress = line.split(" ")[1].trim();
				int port = Integer.parseInt(line.split(" ")[2].trim());
				Bank.InitBranch.Branch.Builder branch = Bank.InitBranch.Branch.newBuilder();
				branch.setName(branchName);
				branch.setIp(ipAddress);
				branch.setPort(port);
				initBranch.addAllBranches(branch);
			}

			initialBalanceOfEachBranch = totalMoney / initBranch.getAllBranchesCount();

			if (totalMoney % initBranch.getAllBranchesCount() != 0) {
				System.err.println("Initial balance must be divisible by number of branches..");
				System.exit(0);
			}
			controller.initBranch(initBranch, initialBalanceOfEachBranch);
			for (int i = 1; i <= 100; i++) {
				startSnapshot(initBranch, i);
			}
			if (bufferedReader != null) {
				bufferedReader.close();
			}
		} catch (IOException ioe) {
			System.err.println(ioe.getMessage());
			ioe.printStackTrace();
			System.exit(0);
		} catch (NumberFormatException e) {
			System.err.println("Initial balance must be Integer." + e.getMessage());
			System.exit(0);
		}
	}

	private void initBranch(Bank.InitBranch.Builder initBranch, int initialBalanceOfEachBranch) throws UnknownHostException, IOException {
		for (Bank.InitBranch.Branch branch : initBranch.getAllBranchesList()) {

			initBranch.setBalance(initialBalanceOfEachBranch);
			String ipAddress = branch.getIp();
			int port = branch.getPort();
//			System.out.println("IP Address : " + ipAddress);
//			System.out.println("Port : " + port);
			Socket socket = new Socket(ipAddress, port);
			Bank.BranchMessage.Builder messageBranch = Bank.BranchMessage.newBuilder();
			messageBranch.setInitBranch(initBranch);
			Bank.BranchMessage branchMessage = messageBranch.build();
			branchMessage.writeDelimitedTo(socket.getOutputStream());
			if (socket != null) {
				socket.close();
			}
		}
	}

	private static void startSnapshot(Bank.InitBranch.Builder initBranch, int snapshotId) {

		try {
			Thread.sleep(1000);

			Random random = new Random();
			int randomBranchNumber = random.nextInt(initBranch.getAllBranchesCount());

			List<Bank.InitBranch.Branch> branchesList = initBranch.getAllBranchesList();
			Bank.InitBranch.Branch randomBranch = branchesList.get(randomBranchNumber);
			Bank.InitSnapshot.Builder initSnapshot = Bank.InitSnapshot.newBuilder();
			initSnapshot.setSnapshotId(snapshotId);
			Bank.InitSnapshot initSnapshotObj = initSnapshot.build();
			Bank.BranchMessage.Builder builder = Bank.BranchMessage.newBuilder();
			Bank.BranchMessage branchMessage = builder.setInitSnapshot(initSnapshotObj).build();
			Socket socket = new Socket(randomBranch.getIp(), randomBranch.getPort());
			branchMessage.writeDelimitedTo(socket.getOutputStream());
			retrieveSnapshot(initBranch, snapshotId);
			if (socket != null) {
				socket.close();
			}
		} catch (Exception ex) {
			System.err.println(ex.getMessage());
			ex.printStackTrace();
		}

	}

	private static void retrieveSnapshot(Bank.InitBranch.Builder initBranch, int snapshotId) {
		try {

			Thread.sleep(5000);
			Bank.RetrieveSnapshot retrieveSnapshot = Bank.RetrieveSnapshot.newBuilder().setSnapshotId(snapshotId).build();
			Bank.BranchMessage branchMessage = Bank.BranchMessage.newBuilder().setRetrieveSnapshot(retrieveSnapshot).build();

			StringBuilder stringBuilder = new StringBuilder();

			stringBuilder.append("============================================================================================\n");
			stringBuilder.append("                        SNAPSHOT_ID : " + snapshotId + "\n");

			int totalbalance = 0;
			branchesMap = new ConcurrentHashMap<>();

			for (int i = 0; i < initBranch.getAllBranchesList().size(); i++) {
				branchesMap.put(initBranch.getAllBranches(i).getName(), initBranch.getAllBranches(i));
			}
			for (Bank.InitBranch.Branch initBranchObj : branchesMap.values()) {
//				Thread.sleep(500);
				Socket socket = new Socket(initBranchObj.getIp(), initBranchObj.getPort());
				branchMessage.writeDelimitedTo(socket.getOutputStream());
				InputStream inputStream = socket.getInputStream();
				Bank.BranchMessage message = Bank.BranchMessage.parseDelimitedFrom(inputStream);
				Bank.ReturnSnapshot.LocalSnapshot localSnapshot = message.getReturnSnapshot().getLocalSnapshot();
				if (localSnapshot != null) {
					totalbalance = totalbalance + localSnapshot.getBalance();
					stringBuilder.append(initBranchObj.getName() + " balance : " + localSnapshot.getBalance());
					int i = 0;
					List<String> branchNameList = new ArrayList<String>();
					for (Bank.InitBranch.Branch branch : branchesMap.values()) {
						if (!initBranchObj.getName().equalsIgnoreCase(branch.getName()))
							branchNameList.add(branch.getName());
					}
					for (int j : localSnapshot.getChannelStateList()) {
						totalbalance = totalbalance + j;

						stringBuilder.append(",\t " + branchNameList.get(i) + " ---- > " + initBranchObj.getName() + " : " + j);
						i++;
					}
					stringBuilder.append("\n");
					socket.close();
				}
			}
			stringBuilder.append("Total balance in Distributed Bank : " + totalbalance + "\n");
			stringBuilder.append("============================================================================================");
			System.out.println(stringBuilder);
		} catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
	}
}
