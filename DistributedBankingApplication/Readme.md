# Implementation of Snapshot Algorithm in Distributed Banking Application
-----------------------------------------------------------------------
## Introduction
This is a implemention of a distributed banking application. The distributed bank has multiple branches. Every branch knows about all other branches. TCP connections are setup between all pairs of branches. Each branch starts with an initial balance. The branch then randomly selects another destination branch and sends a random amount of money to this destination branch at unpredictable times. This project uses Google’s Protocol Buffer for marshalling and unmarshalling messages and uses TCP sockets for sending and receiving these messages.

-----------------------------------------------------------------------
## Overview:
### Controller:

Branches rely on a controller to set their initial balances and get notified of all branches in the distributed bank. This controller takes two command line inputs: the total amount of money in the distributed bank and a local file that stores the names, IP addresses, and port numbers of all branches.

    $> ./controller 4000 branches.txt
    
The file (branches.txt) should contain a list of names, IP addresses, and ports, in the format “<name> <public-ip-address> <port>”, of all of the running branches.

#### For example, if four branches with names: “branch1”, “branch2”, “branch3”, and “branch4” are running on server 128.226.114.201 on port 9090, 9091, 9092, and 9093, then branches.txt should contain:
    branch1 128.226.114.201 9090
    branch2 128.226.114.201 9091
    branch3 128.226.114.201 9092
    branch4 128.226.114.201 9093

The controller will distribute the total amount of money evenly among all branches, e.g., in the example above, every branch will receive $1,000 initial balance. The controller initiates all branches by individually calling the initBranch method described above. Note that the initial balance must be integer.

### Branch:
The  branch executable takes three command line inputs. 
The first one is a human-readable name of the branch, e.g., “branch1”. 
The second one specifies the port number the branch runs on. 
The third input is the maximum interval, in milliseconds, between Transfer messages. For example,

    $> ./branch branch1 9090 1000

## Implementation details:

The Chandy-Lamport global snapshot algorithm has been used to take  global snapshots of distributed  bank application. In case of the distributed bank, a global snapshot will contain both the local state of each branch (i.e., its balance) and the amount of money in transit on all communication channels. Each branch will be responsible for recording and reporting its own local state (balance) as well as the total money in transit on each of its incoming channels.
For simplicity, in this assignment, the controller will contact one of the branches to initiate the global snapshot. It does so by sending a message indicating the InitSnapshot operation to the selected branch. The selected branch will then initiate the snapshot by first recording its own local state and send out Marker messages to all other branches. After some time (long enough for the snapshot algorithm to finish), the controller sends RetrieveSnapshot messages to all branches to retrieve their recorded local and channel states.

### If the snapshot is correct, the total amount of money in all branches and in transit should equal to the command line argument given to the controller.

## Programming language of implementation : JAVA

## To generate Bank.java using protoc :
    $> bash
    $> export PATH=/home/vchaska1/protobuf/bin:$PATH

### cd to DistributedBankingApplication the run command belows :

    $> protoc --java_out=./src/ ./src/bank.proto


## Steps to Compile and Run the program

### 1. Compile the code using Makefile which is already present in the repo.

    $> make

### 2. Run the multiple branch server by providing below command line arguments :
    
    $>./branch <branch-name> <port> <timeinterval>
    
    Collect all running <branch-name> <IP-Address> and <Port> and put them into the branches.txt file.
    
### 3. Run the controller as :
    
    $> ./controller <total-money> <branches.txt>

#### Note: money should be an integer and divisible by total number of branches.   

## Expected  controller output
    ============================================================================================
    SNAPSHOT_ID : 1
    branch3 balance : 1940,     branch2 ---- > branch3 : 0,     branch1 ---- > branch3 : 0
    branch2 balance : 1855,     branch3 ---- > branch2 : 63,     branch1 ---- > branch2 : 0
    branch1 balance : 2142,     branch3 ---- > branch1 : 0,     branch2 ---- > branch1 : 0
    Total balance in Distributed Bank : 6000
    ============================================================================================
    ============================================================================================
    SNAPSHOT_ID : 2
    branch3 balance : 1639,     branch2 ---- > branch3 : 0,     branch1 ---- > branch3 : 0
    branch2 balance : 2310,     branch3 ---- > branch2 : 0,     branch1 ---- > branch2 : 0
    branch1 balance : 2051,     branch3 ---- > branch1 : 0,     branch2 ---- > branch1 : 0
    Total balance in Distributed Bank : 6000
    ============================================================================================
    ============================================================================================
    SNAPSHOT_ID : 3
    branch3 balance : 1430,     branch2 ---- > branch3 : 0,     branch1 ---- > branch3 : 0
    branch2 balance : 2258,     branch3 ---- > branch2 : 148,     branch1 ---- > branch2 : 0
    branch1 balance : 2164,     branch3 ---- > branch1 : 0,     branch2 ---- > branch1 : 0
    Total balance in Distributed Bank : 6000
    ============================================================================================

### Note : Gives balance at each branch as well as their incomming channel balance.
