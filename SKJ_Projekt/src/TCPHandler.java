import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TCPHandler implements Runnable {

    private Socket socket;
    private Map<String, Integer> resources;
    private Map<String, Integer> resourcesInAndUnderMe;

    private String identifier;
    private String upperNodeGateway;
    private int upperNodePort;

    private String myGateway;
    private int myPort;

    private List<String> nodesUnderMeList;

    public TCPHandler(Socket socket, Map<String, Integer> resources, Map<String, Integer> resourcesInAndUnderMe,
                      String upperNodeGateway, int upperNodePort, int myPort, List<String> nodesUnderMeList) {
        this.socket = socket;
        this.resources = resources;
        this.resourcesInAndUnderMe = resourcesInAndUnderMe;
        this.upperNodeGateway = upperNodeGateway;
        this.upperNodePort = upperNodePort;
        this.myPort = myPort;
        this.myGateway = socket.getLocalAddress().getHostAddress();
        this.nodesUnderMeList = nodesUnderMeList;
    }

    @Override
    public void run() {

        try{

            BufferedReader inFromClient = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String line;
            while(!(line = inFromClient.readLine()).isEmpty()) {
                System.out.println("New Message: " + line);

                String[] messageContents = line.split(" ");

                //IF ANOTHER NODE COMMUNICATES
                if(messageContents[0].equals("NN")) {

                    int newNodePort = Integer.parseInt(messageContents[2]);

                    boolean isFirstConnection = false;

                    //IF I AM THE FIRST NODE IT HAS CONNECTED TO
                    if(messageContents[1].equals("FC"))
                        isFirstConnection = true;

                    //ADD ITS RESOURCES TO YOUR 2ND MAP
                    for (int i = 3; i < messageContents.length; i++) {

                        String[] resourcesToAllocate = messageContents[i].split(":");

                        for(int j = 0; j < resourcesToAllocate.length; j+=2)
                        {

                            if(resourcesInAndUnderMe.containsKey(resourcesToAllocate[j])){
                                for(Map.Entry<String, Integer> e : resourcesInAndUnderMe.entrySet())
                                    if(e.getKey().equals(resourcesToAllocate[j]))
                                        e.setValue(e.getValue() + Integer.parseInt(resourcesToAllocate[++j]));
                            }
                            else
                                resourcesInAndUnderMe.put(resourcesToAllocate[j], Integer.parseInt(resourcesToAllocate[++j]));

                        }

                    }

                    //IF THIS IS NOT THE TOP NODE, SEND THEM FURTHER
                    if(!isThisTheTopNode()) {

                        String newResourcesToPass = "";
                        for(int i = 3; i < messageContents.length; i++) {

                            newResourcesToPass += messageContents[i] + " ";

                        }

                        Socket nodeUpperSocket = new Socket(upperNodeGateway, upperNodePort);
                        DataOutputStream outToNode = new DataOutputStream(nodeUpperSocket.getOutputStream());
                        outToNode.writeBytes("NN NFC " + messageContents[2] + " " + newResourcesToPass+"\n");

                        outToNode.close();
                        nodeUpperSocket.close();

                    }

                    //IF THIS IS THE FIRST NODE IT CONNECTED TO
                    if(isFirstConnection) {

                        //GET ITS CONTACT INFO AND ADD TO MY LIST OF CONTACTS
                        String newNodeGateway = socket.getInetAddress().getHostAddress();
                        String newNodeContactInfo = newNodeGateway + ":" + newNodePort;
                        System.out.println("New node connected: " + newNodeContactInfo);
                        nodesUnderMeList.add(newNodeContactInfo);

                    }

                }
                //IF A NODE BELOW HAS ALLOCATED RESOURCES
                else if(messageContents[0].equals("NAR")) {

                    String tempS = "";
                    //REMOVE SAID RESOURCES FROM YOUR 2ND MAP
                    for(int i = 1; i < messageContents.length; i++) {

                        tempS += messageContents[i];
                        String[] resourceAllocated = messageContents[i].split(":");
                        for(Map.Entry<String, Integer> e : resourcesInAndUnderMe.entrySet())
                            if(e.getKey().equals(resourceAllocated[0]))
                                e.setValue(e.getValue() - Integer.parseInt(resourceAllocated[1]));

                    }

                    if(!isThisTheTopNode())
                        tellUpperNodesAboutAllocation(tempS);

                }
                //IF A NODE IS SERVICING A CLIENT
                else if(messageContents[0].equals("NCR")){

                    Map<String, Integer> resourcesToAllocate = stringToMap(messageContents, ":", 1);
                    String currentResource = messageContents[1];
                    String[] currentResourceSplit = currentResource.split(":");
                    int currentResourceNum = Integer.parseInt(currentResourceSplit[1]);

                    InetAddress upperNodeAddress = InetAddress.getByName(upperNodeGateway);
                    DatagramSocket upperNodeSocket = new DatagramSocket();
                    byte[] messageToNode;

                    //IF I ALONE HAVE ENOUGH
                    if(canAllocateResources(resourcesToAllocate, resources)) {

                        for(Map.Entry<String, Integer> e1 : resourcesToAllocate.entrySet())
                            for (Map.Entry<String, Integer> e2 : resources.entrySet())
                                if (e1.getKey().equals(e2.getKey()))
                                    e2.setValue(e2.getValue() - e1.getValue());

                        for(Map.Entry<String, Integer> e1 : resourcesToAllocate.entrySet())
                            for (Map.Entry<String, Integer> e2 : resourcesInAndUnderMe.entrySet())
                                if (e1.getKey().equals(e2.getKey()))
                                    e2.setValue(e2.getValue() - e1.getValue());

                        String resourcesAllocated = mapToSingleLine(resourcesToAllocate);

                        System.out.println("Allocated: " + resourcesAllocated);

                        //CONFIRM ALLOCATING
                        messageToNode = ("NRA " + "BUFFER").getBytes();
                        DatagramPacket outToNodePacket = new DatagramPacket(messageToNode, messageToNode.length,
                                upperNodeAddress, upperNodePort);
                        upperNodeSocket.send(outToNodePacket);
                        upperNodeSocket.send(outToNodePacket);

                    }
                    //IF ME AND MY NODES HAVE ENOUGH
                    else if(canAllocateResources(resourcesToAllocate, resourcesInAndUnderMe)) {

                        boolean hasAllocatedThisOneYet = false;

                        for(String nodeUnderMeAddress : nodesUnderMeList) {

                            if(!hasAllocatedThisOneYet) {

                                String[] nodeAddress = nodeUnderMeAddress.split(":");
                                String nodeGateway = nodeAddress[0];
                                int nodePort = Integer.parseInt(nodeAddress[1]);

                                System.out.println("Sending request to " + nodeGateway + ":" + nodePort + " for "
                                        + currentResourceSplit[0] + ":" + currentResourceNum);
                                Socket lowerNodeSocket = new Socket(nodeGateway, nodePort);
                                DataOutputStream outToNode = new DataOutputStream(lowerNodeSocket.getOutputStream());
                                outToNode.writeBytes("NCR " + currentResourceSplit[0] + ":" + currentResourceNum + "\n");

                                //WAIT FOR ANSWER THROUGH UDP
                                DatagramSocket inFromNodeSocket = new DatagramSocket(myPort);
                                byte[] receivedData = new byte[32768];
                                DatagramPacket receivedPacket = new DatagramPacket(receivedData, receivedData.length);

                                inFromNodeSocket.receive(receivedPacket);
                                String messageFromNode = new String(receivedPacket.getData(), 0, receivedData.length);
                                String[] messageFromNodeParts = messageFromNode.split(" ");
                                String nodeFlag = messageFromNodeParts[0];
                                inFromNodeSocket.close();
                                System.out.println(messageFromNode);

                                if (nodeFlag.equals("NRA")) {

                                    hasAllocatedThisOneYet = true;
                                    messageToNode = ("NRA_R " + nodeGateway + " " + nodePort + " " +
                                                     "BUFFER").getBytes();
                                    DatagramPacket outToNodePacket = new DatagramPacket(messageToNode, messageToNode.length,
                                            upperNodeAddress, upperNodePort);
                                    upperNodeSocket.send(outToNodePacket);

                                    for(Map.Entry<String, Integer> e1 : resourcesToAllocate.entrySet())
                                        for (Map.Entry<String, Integer> e2 : resourcesInAndUnderMe.entrySet())
                                            if (e1.getKey().equals(e2.getKey()))
                                                e2.setValue(e2.getValue() - e1.getValue());


                                }
                                else if (nodeFlag.equals("NRA_R")) {

                                    hasAllocatedThisOneYet = true;
                                    messageToNode = ("NRA_R " + messageFromNodeParts[1] + " " + messageFromNodeParts[2] + " " +
                                            "BUFFER").getBytes();
                                    DatagramPacket outToNodePacket = new DatagramPacket(messageToNode, messageToNode.length,
                                            upperNodeAddress, upperNodePort);
                                    upperNodeSocket.send(outToNodePacket);

                                    for(Map.Entry<String, Integer> e1 : resourcesToAllocate.entrySet())
                                        for (Map.Entry<String, Integer> e2 : resourcesInAndUnderMe.entrySet())
                                            if (e1.getKey().equals(e2.getKey()))
                                                e2.setValue(e2.getValue() - e1.getValue());


                                }

                            }

                        }

                        if(!hasAllocatedThisOneYet) {

                            messageToNode = ("Failed To Allocate " + "BUFFER").getBytes();
                            DatagramPacket outToNodePacket = new DatagramPacket(messageToNode, messageToNode.length,
                                    upperNodeAddress, upperNodePort);
                            upperNodeSocket.send(outToNodePacket);

                        }

                    }
                    //IF ME AND MY NODES DON'T HAVE ENOUGH
                    else {

                        messageToNode = ("NRF " + "BUFFER").getBytes();
                        DatagramPacket outToNodePacket = new DatagramPacket(messageToNode, messageToNode.length,
                                upperNodeAddress, upperNodePort);
                        upperNodeSocket.send(outToNodePacket);

                    }

                    upperNodeSocket.close();

                }
                //IF TOLD TO TERMINATE
                else if(messageContents[0].equals("TERMINATE")) {

                    System.out.println("TERMINATE FLAG GIVEN");

                    //ONLY IF THE MESSAGE CAME FROM THE CLIENT
                    if(messageContents.length == 1) {

                        //SEND IT TO MY LOWER NODES
                        for (String s : nodesUnderMeList) {

                            System.out.println("Sending terminate signal to " + s);
                            String[] nodeInfo = s.split(":");
                            String gateway = nodeInfo[0];
                            int port = Integer.parseInt(nodeInfo[1]);

                            Socket nodeTerminateSocket = new Socket(gateway, port);
                            PrintWriter outToNode = new PrintWriter(nodeTerminateSocket.getOutputStream(), true);
                            outToNode.println("TERMINATE " + myGateway + " " + myPort);

                        }

                        //SEND IT TO MY UPPER NODE
                        if(!isThisTheTopNode())
                        {

                            Socket nodeUpperSocket = new Socket(upperNodeGateway, upperNodePort);
                            System.out.println("Sending terminate signal to my upper node at " + upperNodeGateway + ":" + upperNodePort);
                            PrintWriter outToNode = new PrintWriter(nodeUpperSocket.getOutputStream(), true);
                            outToNode.println("TERMINATE " + myGateway + " " + myPort);

                        }

                    }
                    //DON'T SEND IT TO THE LOWER NODE THAT HAS ALREADY TERMINATED ME
                    else if(messageContents.length == 3) {

                        String terminatingNodeGateway = messageContents[1];
                        int terminatingNodePort = Integer.parseInt(messageContents[2]);

                        //SEND IT TO MY LOWER NODES
                        for (String s : nodesUnderMeList) {

                            String[] nodeInfo = s.split(":");
                            String gateway = nodeInfo[0];
                            int port = Integer.parseInt(nodeInfo[1]);

                            if(terminatingNodeGateway.equals(gateway) && terminatingNodePort == port) {
                                System.out.println("Not Sending Terminate Signal Back To Its Source At " + terminatingNodeGateway + ":" + terminatingNodePort);
                            }
                            else {

                                Socket nodeTerminateSocket = new Socket(gateway, port);
                                PrintWriter outToNode = new PrintWriter(nodeTerminateSocket.getOutputStream(), true);
                                outToNode.println("TERMINATE");
                                System.out.println("Sending terminate signal to my lower node at " + s);

                            }

                        }

                        //SEND IT TO MY UPPER NODE
                        if(!isThisTheTopNode()) {

                            if(terminatingNodeGateway.equals(upperNodeGateway) && terminatingNodePort == upperNodePort) {
                                System.out.println("Not Sending Terminate Signal Back To Its Source At " + terminatingNodeGateway + ":" + terminatingNodePort);
                            }
                            else {

                                Socket nodeUpperSocket = new Socket(upperNodeGateway, upperNodePort);
                                System.out.println("Sending terminate signal to my upper node at " + upperNodeGateway + ":" + upperNodePort);
                                PrintWriter outToNode = new PrintWriter(nodeUpperSocket.getOutputStream(), true);
                                outToNode.println("TERMINATE " + myGateway + " " + myPort);

                            }

                        }

                    }


                    System.out.println("TERMINATING MYSELF");
                    System.exit(1);

                }
                //IF A NODE WANTS TO ASK ABOUT THE SYSTEM'S RESOURCES
                else if(messageContents[0].equals("NRU")) {

                    List<String> allocationsList = new ArrayList<>();

                    InetAddress requestNodeAddress = InetAddress.getByName(messageContents[1]);
                    int requestNodePort = Integer.parseInt(messageContents[2]);
                    Map<String, Integer> resourcesToAllocate = stringToMap(messageContents, ":", 3);
                    String resourcesInLine = mapToSingleLine(resourcesToAllocate);
                    String[] resourceArr = resourcesInLine.split(" ");

                    DatagramSocket upperNodeSocket = new DatagramSocket();
                    byte[] messageToNode;

                    //IF ME AND MY NODES HAVE ENOUGH
                    if(canAllocateResources(resourcesToAllocate, resourcesInAndUnderMe)) {

                        for(String currentResource : resourceArr) {

                            Map<String, Integer> resourceToAllocateWithinMe = new TreeMap<>();
                            String[] currentResTemp = currentResource.split(":");
                            String resourceType = currentResTemp[0];
                            int resourceNum = Integer.parseInt(currentResTemp[1]);
                            int tempNum = resourceNum;

                            boolean hasAllocatedResourcesThisIteration;

                            while(resourceNum > 0) {

                                hasAllocatedResourcesThisIteration = false;

                                System.out.println("STILL NEEDED: " + resourceType + ":" + resourceNum);
                                System.out.println("NOW LOOKING TO ALLOCATE: " + resourceType + ":" + tempNum);


                                resourceToAllocateWithinMe.put(resourceType, tempNum);

                                //TRY TO ALLOCATE RESOURCES IN ME
                                if (canAllocateResources(resourceToAllocateWithinMe, resources)) {

                                    System.out.println("ALLOCATING " + resourceType + ":" + tempNum + " HERE");

                                    for(Map.Entry<String, Integer> e1 : resourceToAllocateWithinMe.entrySet())
                                        for (Map.Entry<String, Integer> e2 : resources.entrySet())
                                            if (e1.getKey().equals(e2.getKey()))
                                                e2.setValue(e2.getValue() - e1.getValue());

                                    for(Map.Entry<String, Integer> e1 : resourceToAllocateWithinMe.entrySet())
                                        for (Map.Entry<String, Integer> e2 : resourcesInAndUnderMe.entrySet())
                                            if (e1.getKey().equals(e2.getKey()))
                                                e2.setValue(e2.getValue() - e1.getValue());

                                    hasAllocatedResourcesThisIteration = true;

                                    String successfulAllocationData = "";
                                    successfulAllocationData += resourceType + ":" + tempNum + ":" + myGateway + ":" + myPort;

                                    allocationsList.add(successfulAllocationData);

                                    resourceNum -= tempNum;

                                    if(tempNum > resourceNum)
                                        tempNum = resourceNum;

                                }
                                //IF DIDN'T, ASK NODES UNDER ME
                                if(!hasAllocatedResourcesThisIteration) {
                                //IF DIDN'T ALLOCATE ANYTHING MYSELF
                                    for (String nodeUnderMeAddress : nodesUnderMeList) {

                                        //ASK MY NODES


                                        String[] nodeAddress = nodeUnderMeAddress.split(":");
                                        String nodeGateway = nodeAddress[0];
                                        int nodePort = Integer.parseInt(nodeAddress[1]);

                                        System.out.println("Sending request to " + nodeGateway + ":" + nodePort + " for "
                                                + resourceType + ":" + tempNum);
                                        Socket lowerNodeSocket = new Socket(nodeGateway, nodePort);
                                        DataOutputStream outToNode = new DataOutputStream(lowerNodeSocket.getOutputStream());
                                        outToNode.writeBytes("NCR " + resourceType + ":" + tempNum + "\n");

                                        //WAIT FOR ANSWER THROUGH UDP
                                        DatagramSocket inFromNodeSocket = new DatagramSocket(myPort);
                                        byte[] receivedData = new byte[32768];
                                        DatagramPacket receivedPacket = new DatagramPacket(receivedData, receivedData.length);

                                        inFromNodeSocket.receive(receivedPacket);
                                        String messageFromNode = new String(receivedPacket.getData(), 0, receivedData.length);
                                        String[] messageFromNodeParts = messageFromNode.split(" ");
                                        String nodeFlag = messageFromNodeParts[0];
                                        inFromNodeSocket.close();

                                        System.out.println(nodeFlag);

                                        if (nodeFlag.equals("NRA")) {

                                            hasAllocatedResourcesThisIteration = true;
                                            String successfulAllocationData = resourceType + ":" + tempNum + ":" +
                                                    nodeGateway + ":" + nodePort;
                                            allocationsList.add(successfulAllocationData);

                                            resourceNum -= tempNum;

                                            if (tempNum > resourceNum)
                                                tempNum = resourceNum;

                                            for (Map.Entry<String, Integer> e1 : resourceToAllocateWithinMe.entrySet())
                                                for (Map.Entry<String, Integer> e2 : resourcesInAndUnderMe.entrySet())
                                                    if (e1.getKey().equals(e2.getKey()))
                                                        e2.setValue(e2.getValue() - e1.getValue());

                                        }
                                        else if (nodeFlag.equals("NRA_R")) {

                                            hasAllocatedResourcesThisIteration = true;
                                            String successfulAllocationData = resourceType + ":" + tempNum + ":" +
                                                    messageFromNodeParts[1] + ":" +
                                                    messageFromNodeParts[2];
                                            allocationsList.add(successfulAllocationData);

                                            for (Map.Entry<String, Integer> e1 : resourceToAllocateWithinMe.entrySet())
                                                for (Map.Entry<String, Integer> e2 : resourcesInAndUnderMe.entrySet())
                                                    if (e1.getKey().equals(e2.getKey()))
                                                        e2.setValue(e2.getValue() - e1.getValue());

                                            resourceNum -= tempNum;

                                            if (tempNum > resourceNum)
                                                tempNum = resourceNum;

                                        }

                                    }

                                }

                                if (!hasAllocatedResourcesThisIteration)
                                    tempNum--;

                                if (tempNum < 1)
                                    tempNum = 1;

                            }

                        }

                        String allAllocations = "";
                        for(String s : allocationsList)
                            allAllocations += s + " ";

                        //SEND ALL SUCCESSFUL ALLOCATIONS INFO BACK TO THE NODE
                        messageToNode = ("NRA " + allAllocations + "BUFFER").getBytes();
                        DatagramPacket outToNodePacket = new DatagramPacket(messageToNode, messageToNode.length,
                                requestNodeAddress, requestNodePort);
                        upperNodeSocket.send(outToNodePacket);

                    }
                    //IF WE DON'T, PASS THE REQUEST UP AND CHECK THE SAME THING THERE
                    else if(!isThisTheTopNode()) {

                        Socket nodeUpperSocket = new Socket(upperNodeGateway, upperNodePort);
                        DataOutputStream outToNode = new DataOutputStream(nodeUpperSocket.getOutputStream());
                        System.out.println("Passing " + line + "up");
                        outToNode.writeBytes(line + "\n");
                        outToNode.close();
                        nodeUpperSocket.close();

                    }
                    //IF THIS IS THE TOP NODE AND THE SYSTEM DOESN'T HAVE ENOUGH
                    else {

                        messageToNode = ("NRF " + "BUFFER").getBytes();
                        DatagramPacket outToNodePacket = new DatagramPacket(messageToNode, messageToNode.length,
                                requestNodeAddress, requestNodePort);
                        upperNodeSocket.send(outToNodePacket);

                    }

                }
                //IF CLIENT COMMUNICATES
                else {

                    identifier = messageContents[0];
                    String clientGateway = socket.getInetAddress().getHostAddress();
                    int clientPort = socket.getPort();

                    System.out.println("CLIENT " + identifier + " CONNECTING ON " + clientGateway + ":" + clientPort);

                    Map<String, Integer> resourcesToAllocate = stringToMap(messageContents, ":", 1);
                    String resourcesWantedLine = mapToSingleLine(resourcesToAllocate);

                    System.out.println("Client Wants: " + resourcesWantedLine);

                    //IF I ALONE HAVE ENOUGH
                    if(canAllocateResources(resourcesToAllocate, resources)) {

                        for(Map.Entry<String, Integer> e1 : resourcesToAllocate.entrySet())
                            for (Map.Entry<String, Integer> e2 : resources.entrySet())
                                if (e1.getKey().equals(e2.getKey()))
                                    e2.setValue(e2.getValue() - e1.getValue());

                        for(Map.Entry<String, Integer> e1 : resourcesToAllocate.entrySet())
                            for (Map.Entry<String, Integer> e2 : resourcesInAndUnderMe.entrySet())
                                if (e1.getKey().equals(e2.getKey()))
                                    e2.setValue(e2.getValue() - e1.getValue());

                        String resourcesDeallocated = mapToSingleLine(resourcesToAllocate);

                        System.out.println("Deallocated: " + resourcesDeallocated);

                        //TELL UPPER NODES THAT RESOURCES HAVE BEEN DEALLOCATED
                        if(!isThisTheTopNode())
                            tellUpperNodesAboutAllocation(resourcesDeallocated);

                        //SEND
                        writeMessageToClient("ALLOCATED");
                        String[] resourceList = resourcesDeallocated.split(" ");

                        for(String s : resourceList) {

                            writeMessageToClient(s + ":" + myGateway + ":" + myPort);

                        }

                    }
                    //IF ME AND MY LOWER NODES HAVE ENOUGH
                    else if(canAllocateResources(resourcesToAllocate, resourcesInAndUnderMe)) {

                        writeMessageToClient("ALLOCATED");

                        String[] resourcesSeparated = resourcesWantedLine.split(" ");

                        //GO THROUGH RESOURCES ONE BY ONE
                        for(String currentResource : resourcesSeparated) {

                            Map<String, Integer> resourcesToAllocateWithinMe = new TreeMap<>();

                            String[] currentResourceValues = currentResource.split(":");

                            int currentResourceNum = Integer.parseInt(currentResourceValues[1]);
                            int tempNum = currentResourceNum;

                            while(currentResourceNum > 0) {

                                boolean assignedResourcesThisIteration = false;

                                System.out.println("STILL NEEDED: " + currentResourceValues[0]+":"+currentResourceNum);
                                System.out.println("NOW LOOKING TO ALLOCATE " + currentResourceValues[0] + ":" + tempNum);

                                //LOOK AT MY RESOURCES FIRST
                                System.out.println("TRYING TO ALLOCATE " + currentResourceValues[0]+":" + tempNum + " HERE");
                                resourcesToAllocateWithinMe.put(currentResourceValues[0], tempNum);
                                if(canAllocateResources(resourcesToAllocateWithinMe, resources)) {

                                    for(Map.Entry<String, Integer> e1 : resourcesToAllocateWithinMe.entrySet())
                                        for (Map.Entry<String, Integer> e2 : resources.entrySet())
                                            if (e1.getKey().equals(e2.getKey()))
                                                e2.setValue(e2.getValue() - e1.getValue());

                                    for(Map.Entry<String, Integer> e1 : resourcesToAllocateWithinMe.entrySet())
                                        for (Map.Entry<String, Integer> e2 : resourcesInAndUnderMe.entrySet())
                                            if (e1.getKey().equals(e2.getKey()))
                                                e2.setValue(e2.getValue() - e1.getValue());

                                    String resourcesAllocated = mapToSingleLine(resourcesToAllocateWithinMe);
                                    System.out.println("Allocated: " + resourcesAllocated);

                                    assignedResourcesThisIteration = true;
                                    writeMessageToClient(currentResourceValues[0] + ":" + tempNum + ":"
                                            + myGateway + ":" + myPort);
                                    //TELL UPPER NODES THAT RESOURCES HAVE BEEN DEALLOCATED
                                    if(!isThisTheTopNode())
                                        tellUpperNodesAboutAllocation(currentResourceValues[0] + ":" + tempNum);

                                    currentResourceNum -= tempNum;

                                }
                                //THEN ASK MY NODES
                                for (String nodeUnderMeAddress : nodesUnderMeList) {

                                    if (!assignedResourcesThisIteration) {

                                        String[] nodeAddress = nodeUnderMeAddress.split(":");
                                        String nodeGateway = nodeAddress[0];
                                        int nodePort = Integer.parseInt(nodeAddress[1]);

                                        System.out.println("Sending request to " + nodeGateway + ":" + nodePort + " for "
                                                + currentResourceValues[0] + ":" + tempNum);
                                        Socket lowerNodeSocket = new Socket(nodeGateway, nodePort);
                                        DataOutputStream outToNode = new DataOutputStream(lowerNodeSocket.getOutputStream());
                                        outToNode.writeBytes("NCR " + currentResourceValues[0] + ":" + tempNum + "\n");

                                        //WAIT FOR ANSWER THROUGH UDP
                                        DatagramSocket inFromNodeSocket = new DatagramSocket(myPort);
                                        byte[] receivedData = new byte[32768];
                                        DatagramPacket receivedPacket = new DatagramPacket(receivedData, receivedData.length);

                                        inFromNodeSocket.receive(receivedPacket);
                                        String messageFromNode = new String(receivedPacket.getData(), 0, receivedData.length);
                                        String[] messageFromNodeParts = messageFromNode.split(" ");
                                        String nodeFlag = messageFromNodeParts[0];
                                        inFromNodeSocket.close();

                                        System.out.println(messageFromNode);

                                        if (nodeFlag.equals("NRA")) {

                                            assignedResourcesThisIteration = true;
                                            writeMessageToClient(currentResourceValues[0] + ":" + tempNum + ":"
                                                                    + nodeGateway + ":" + nodePort);
                                            currentResourceNum -= tempNum;

                                            for(Map.Entry<String, Integer> e1 : resourcesToAllocateWithinMe.entrySet())
                                                for (Map.Entry<String, Integer> e2 : resourcesInAndUnderMe.entrySet())
                                                    if (e1.getKey().equals(e2.getKey()))
                                                        e2.setValue(e2.getValue() - e1.getValue());

                                            //TELL UPPER NODES THAT RESOURCES HAVE BEEN DEALLOCATED
                                            if(!isThisTheTopNode())
                                                tellUpperNodesAboutAllocation(currentResourceValues[0] + ":" + tempNum);

                                        }
                                        else if(nodeFlag.equals("NRA_R")) {

                                            assignedResourcesThisIteration = true;
                                            writeMessageToClient(currentResourceValues[0] + ":" + tempNum + ":"
                                                                    + messageFromNodeParts[1] + ":" + messageFromNodeParts[2]);
                                            currentResourceNum -= tempNum;

                                            for(Map.Entry<String, Integer> e1 : resourcesToAllocateWithinMe.entrySet())
                                                for (Map.Entry<String, Integer> e2 : resourcesInAndUnderMe.entrySet())
                                                    if (e1.getKey().equals(e2.getKey()))
                                                        e2.setValue(e2.getValue() - e1.getValue());

                                            //TELL UPPER NODES THAT RESOURCES HAVE BEEN DEALLOCATED
                                            if(!isThisTheTopNode())
                                                tellUpperNodesAboutAllocation(currentResourceValues[0] + ":" + tempNum);

                                        }

                                    }

                                }

                                if(!assignedResourcesThisIteration)
                                    tempNum--;

                                if(tempNum < 1)
                                    tempNum = 1;

                            }

                        }

                    }
                    //IF WE DON'T, ASK THE UPPER NODE IF THERE IS ENOUGH
                    else {

                        //IF THIS IS NOT THE TOP, SEND A FLAG TO YOUR UPPER NODE AND AWAIT RESPONSE
                        if(!isThisTheTopNode()) {

                            String resourceList = mapToSingleLine(resourcesToAllocate);

                            int customPort = 5000;
                            Socket nodeUpperSocket = new Socket(upperNodeGateway, upperNodePort);
                            DataOutputStream outToNode = new DataOutputStream(nodeUpperSocket.getOutputStream());
                            outToNode.writeBytes("NRU " + myGateway + " " + customPort + " " + resourceList + "\n");
                            outToNode.close();
                            nodeUpperSocket.close();

                            System.out.println("NRU " + myGateway + " " + customPort + " " + resourceList);

                            DatagramSocket inFromNodeSocket = new DatagramSocket(customPort);
                            byte[] receivedData = new byte[32768];
                            DatagramPacket receivedPacket = new DatagramPacket(receivedData, receivedData.length);

                            inFromNodeSocket.receive(receivedPacket);
                            String messageFromNode = new String(receivedPacket.getData(), 0, receivedData.length);
                            String[] messageFromNodeFragments = messageFromNode.split(" ");
                            String nodeFlag = messageFromNodeFragments[0];
                            inFromNodeSocket.close();

                            System.out.println(nodeFlag);

                            if(nodeFlag.equals("NRA")) {

                                writeMessageToClient("ALLOCATED");
                                for(int i = 1; i < messageFromNodeFragments.length - 1; i++)
                                    writeMessageToClient(messageFromNodeFragments[i]);

                            }
                            else if(nodeFlag.equals("NRF")) {

                                System.out.println("Not Enough Resources To Allocate: " + mapToSingleLine(resourcesToAllocate));
                                writeMessageToClient("FAILED");

                            }
                            else
                                System.err.println("Unknown Flag");



                        }
                        //IF THIS IS THE TOP (AND DOES NOT HAVE ENOUGH RESOURCES IN THE SYSTEM) DON'T ALLOCATE ANY RESOURCES AND NOTIFY CLIENT
                        else {

                            System.out.println("Not Enough Resources To Allocate: " + mapToSingleLine(resourcesToAllocate));
                            writeMessageToClient("FAILED");

                        }

                    }

                    inFromClient.close();
                    socket.close();

                }

                getMyResourceStatus();

            }
        } catch (IOException e) {}

    }

    public void getMyResourceStatus() {
        String tempResourcesStored = mapToSingleLine(resources);
        String tempResourcesUnderMeStored = mapToSingleLine(resourcesInAndUnderMe);
        System.out.println("My Available Resources: " + tempResourcesStored);
        System.out.println("Resources Available Under Me: " + tempResourcesUnderMeStored);
    }

    public boolean isThisTheTopNode() {
        if(this.upperNodeGateway != null && this.upperNodePort != 0)
            return false;
        return true;
    }

    public boolean canAllocateResources(Map<String, Integer> resourcesToAllocate, Map<String, Integer> resourcesAvailable) {

        for(Map.Entry<String, Integer> e1 : resourcesToAllocate.entrySet()) {

            boolean keyFound = false;
            for (Map.Entry<String, Integer> e2 : resourcesAvailable.entrySet())
                if (e1.getKey().equals(e2.getKey())) {
                    keyFound = true;
                    if (e1.getValue() > e2.getValue())
                        return false;
                }

            if (!keyFound)
                return false;

        }

        return true;

    }

    public void writeMessageToClient(String s) throws IOException {

        PrintWriter outToClient = new PrintWriter(socket.getOutputStream(), true);
        outToClient.println(s);

    }

    public void tellUpperNodesAboutAllocation(String s) throws IOException {

        Socket nodeUpperSocket = new Socket(upperNodeGateway, upperNodePort);
        DataOutputStream outToNode = new DataOutputStream(nodeUpperSocket.getOutputStream());
        outToNode.writeBytes("NAR " + s + "\n");

    }

    public String mapToSingleLine(Map<String, Integer> map) {
        String temp = "";
        for(Map.Entry<String, Integer> e : map.entrySet())
            temp += (e.getKey() + ":" + e.getValue()) + " ";
        return temp;
    }

    public Map<String, Integer> stringToMap(String[] stringsToMap, String regexSplit, int start) {

        Map<String, Integer> tempMap = new TreeMap<>();

        for(int i = start; i < stringsToMap.length; i++)
        {

            String[] resourcesArray = stringsToMap[i].split(regexSplit);
            tempMap.put(resourcesArray[0], Integer.parseInt(resourcesArray[1]));

        }

        return tempMap;

    }



}
