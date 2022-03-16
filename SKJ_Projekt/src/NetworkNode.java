import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkNode {

    public static void main(String[] args) {

        String identifier = null;
        String gateway = null;
        String resourcesStored = "";
        int gatewayPort = 0;
        int tcpPort = 0;

        List<String> nodesUnderMeList = new ArrayList<>();

        int threadsNum = 256;

        boolean isFirstNode = true;

        Map<String, Integer> resources = new TreeMap<>();
        Map<String, Integer> resourcesInAndUnderMe = new TreeMap<>();

        // Parameter scan loop
        for(int i=0; i<args.length; i++) {

            switch (args[i]) {
                case "-ident":
                    identifier = args[++i];
                    break;
                case "-tcpport":
                    tcpPort = Integer.parseInt(args[++i]);
                    break;
                case "-gateway":
                    String[] gatewayArray = args[++i].split(":");
                    gateway = gatewayArray[0];
                    gatewayPort = Integer.parseInt(gatewayArray[1]);
                    isFirstNode = false;
                    break;
                default:
                    String[] resourcesArray = args[i].split(":");
                    resources.put(resourcesArray[0], Integer.parseInt(resourcesArray[1]));
                    resourcesInAndUnderMe.put(resourcesArray[0], Integer.parseInt(resourcesArray[1]));
            }

        }

        for(Map.Entry<String, Integer> e : resources.entrySet())
            resourcesStored += (e.getKey()+":"+e.getValue()+" ");
        System.out.println("My Available Resources: " + resourcesStored);

        try{

            ExecutorService threadPool = Executors.newFixedThreadPool(threadsNum);

            //CONNECTING TO THE NETWORK
            if(!isFirstNode) {

                Socket nodeUpperSocket = new Socket(gateway, gatewayPort);
                DataOutputStream outToNode = new DataOutputStream(nodeUpperSocket.getOutputStream());
                outToNode.writeBytes("NN FC " + tcpPort + " " + resourcesStored+"\n");

            }

            //LISTENING FOR CLIENTS AND/OR OTHER NODES
            ServerSocket nodeLowerSocket = new ServerSocket(tcpPort);
            while(true) {

                threadPool.submit(new TCPHandler(nodeLowerSocket.accept(), resources, resourcesInAndUnderMe,
                                                 gateway, gatewayPort, tcpPort, nodesUnderMeList));

            }

        }catch(IOException e){
            e.printStackTrace();
        }


    }
}
