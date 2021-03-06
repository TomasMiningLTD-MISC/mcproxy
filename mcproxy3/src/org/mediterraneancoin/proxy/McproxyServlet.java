package org.mediterraneancoin.proxy;

import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.mediterraneancoin.miner.SuperHasher;
import static org.mediterraneancoin.proxy.McproxyHandler.DEBUG;
import org.mediterraneancoin.proxy.McproxyHandler.SessionStorage;
import static org.mediterraneancoin.proxy.McproxyHandler.utils;
import static org.mediterraneancoin.proxy.McproxyHandler.works;
import org.mediterraneancoin.proxy.net.RPCUtils;
import static org.mediterraneancoin.proxy.net.RPCUtils.tohex;
import org.mediterraneancoin.proxy.net.WorkState;

/**
 *
 * @author dev3
 */
public class McproxyServlet extends HttpServlet {
    
    public static boolean DEBUG = false;
    
    static int localport;
    static String hostname;
    static int port;
    
    
    final ObjectMapper mapper = new ObjectMapper();
    public static URL url;
    public static RPCUtils utils;
    
    static HashMap<String, SessionStorage> works = new HashMap<String, SessionStorage>();
     
    

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        
        
        if (false) {
            response.setContentType("application/json");

            response.setStatus(HttpServletResponse.SC_OK);
            //baseRequest.setHandled(true);



            response.getWriter().println("{\"result\":{\"data\":\"669e7ea57ea1ed30af18734e199a428ff6645445053c7a7479af7cbfed77e3090fe7fd4b2573a668e3d5c2485db61d3457949d799799633a3e01c3541d85afb17873628252f182fe1b0449f500000000000000800000000000000000000000000000000000000000000000000000000000000000000000000000000080020000\",\"target\":\"000000000000000000000000000000000000000000000000f549040000000000\"},\"error\":null,\"id\":1}");                
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(McproxyServlet.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            return;
        }

        if (DEBUG) {  
            System.out.println("method: " + request.getMethod());
        }        
        
        String type = request.getContentType(); 
        
        String authHeader = request.getHeader("authorization");
        int contentLength = Integer.parseInt( request.getHeader("content-length") );
        
        if (GetworkThread.getAuthHeader() == null)
            GetworkThread.setAuthHeader(authHeader);        
        
        if (DEBUG) {
            System.out.println("auth: " + authHeader);
            System.out.println("content-type: " + type );
        }
        
        
        byte cbuf[] = new byte[contentLength]; 
        
        request.getInputStream().read(cbuf);
        
        String content = new String(cbuf); 
 

        if (DEBUG) {  
          System.out.println("content-len: " + contentLength);          
          System.out.println("content: " + content);          
        }        
        


        ObjectNode node = null;

        try {
            node = (ObjectNode) mapper.readTree(content);
 
        } catch (IOException ex) {
            Logger.getLogger(McproxyHandler.class.getName()).log(Level.SEVERE, null, ex);
        } 


        String jsonMethod = node.get("method").asText();

        if (DEBUG) {
          System.out.println("jsonMethod: " + jsonMethod);
        }

        int paramSize = -1;

        if (node.get("params").isArray()) {
            paramSize = node.get("params").size();
        }

        String id = node.get("id").asText();


        String answer = "";
          
        if (type.toString().equals("application/json") && jsonMethod.equals("getwork") && paramSize == 0) {
            
            if (false) {
                System.out.println("getwork request...");

                WorkState work = null;
                try {
                    work = utils.doGetWorkMessage(false,authHeader);
                } catch (IOException ex) {
                    Logger.getLogger(McproxyHandler.class.getName()).log(Level.SEVERE, null, ex);
                }

                String dataFromWallet = work.getAllDataHex();

                if (DEBUG) {  
                    // data has already been byteswapped inside doGetWorkMessage
                    System.out.println("data: " + dataFromWallet);              
                    System.out.println("target: " + work.getTarget());
                }


                SuperHasher hasher = null;
                try {
                    hasher = new SuperHasher();
                } catch (GeneralSecurityException ex) {
                    Logger.getLogger(McproxyHandler.class.getName()).log(Level.SEVERE, null, ex);
                }



                byte [] part1 = null;
                try {
                    part1 = hasher.firstPartHash(work.getData1() );
                } catch (GeneralSecurityException ex) {
                    Logger.getLogger(McproxyHandler.class.getName()).log(Level.SEVERE, null, ex);
                }

                if (DEBUG) { 
                    System.out.println("part1: " + tohex(part1));
                    System.out.println();                
                }

                ObjectNode resultNode = mapper.createObjectNode();

                // we need to byteswap data before sending it
                String tempData = tohex(part1) + tohex(work.getData2());

                String dataStr = WorkState.byteSwap( tempData );

                resultNode.put("data", dataStr );
                resultNode.put("target", work.getTarget());                

                ObjectNode answerNode = mapper.createObjectNode();
                answerNode.put("result", resultNode);
                answerNode.put("error", (String)null);
                answerNode.put("id", Long.parseLong(id) );        

                answer = answerNode.toString();


                SessionStorage sessionStorage = new SessionStorage();
                sessionStorage.work = work;
                sessionStorage.sentData = dataStr;
                sessionStorage.dataFromWallet = dataFromWallet;

                works.put(dataStr.substring(0, 68*2) , sessionStorage);



                if (DEBUG) { 
                    System.out.println("json: " + answer);
                    System.out.println();
                    System.out.println();
                }
            } else {
                
                System.out.println("getwork request from miner...");
                
                McproxyHandler.SessionStorage sessionStorage = GetworkThread.getSessionStorage();
                
                works.put(sessionStorage.sentData.substring(0, 68*2) , sessionStorage);
                
                answer = sessionStorage.answer;                
                
            }

        } else if (type.toString().equals("application/json") && jsonMethod.equals("getwork") && paramSize != 0) {

            System.out.println("submitwork request from miner...");

            String receivedDataStr = node.get("params").get(0).asText();
            //SuperHasher hasher 
            SessionStorage sessionStorage = works.get(receivedDataStr.substring(0, 68*2));



            if (sessionStorage == null) {
                System.out.println("WORK NOT FOUND!!! " + receivedDataStr);

                answer = "{\"result\":false,\"error\":null,\"id\":1}";

            } else {

                WorkState work = sessionStorage.work;

                if (DEBUG) { 
                    System.out.println("RECEIVED WORK: " + receivedDataStr);
                    System.out.println("dataFromWallet: " + sessionStorage.dataFromWallet);
                    System.out.println("sentData TO MINER: " + sessionStorage.sentData);
                }

                String workStr = WorkState.byteSwap(sessionStorage.dataFromWallet.substring(0, 68*2)) +
                        receivedDataStr.substring(68*2);

                  // send to wallet
                  // need to byteswap! it's done inside doSendWorkMessage                    
                boolean result = false;
                try {
                    result = utils.doSendWorkMessage(workStr,authHeader);
                } catch (IOException ex) {
                    Logger.getLogger(McproxyHandler.class.getName()).log(Level.SEVERE, null, ex);
                }


                /*
                //

                // 1 - byteswap all data received from miner
                receivedDataStr = WorkState.byteSwap(receivedDataStr);


                String nonceStr = receivedDataStr.substring(76*2, 76*2 + 8);              
                System.out.println("byteswapped nonce: " + nonceStr);


                // copy nonce from received work (and also nTime and nBits) to original work, a total of 12 bytes
                byte [] data = work.getData1();

                for (int i = 0; i < 24; i += 2) {                   
                      String n = receivedDataStr.substring(i, i + 2);

                      data[68 + i / 2] =  (byte) (0xFF & Integer.parseInt(n, 16)); //Byte.parseByte(n, 16);                          
                }


                // 2 - calculate part2 of hybridhash
                SuperHasher hasher = new SuperHasher();

                byte [] targetBits = new byte[4];
                targetBits[0] = work.getData1()[75];
                targetBits[1] = work.getData1()[74];
                targetBits[2] = work.getData1()[73];
                targetBits[3] = work.getData1()[72];    

                byte[] finalHash = hasher.secondPartHash(data, targetBits);

                // 2.1 - verify hash
                byte [] header = work.getData1();
                BigInteger hashTarget = readCompact(header[75], header[74], header[73],header[72]);

                //System.out.println("hashTarget: " + hashTarget);
                System.out.println("hashTarget: " + hashTarget.toString(16));                

                BigInteger hash = new BigInteger( 1 , SuperHasher.swap(finalHash) );

                boolean checkHash =  hash.compareTo(hashTarget) <= 0;

                System.out.println("hash: " + hash.toString(16));  

                System.out.println("is hash ok? " + checkHash);
                */
                //


                works.remove(receivedDataStr.substring(0, 68*2));

                answer = "{\"result\":" + result + ",\"error\":null,\"id\":1}";
              }




        }        
        
        
        
        
        
        response.setContentType("application/json");
        
        response.setStatus(HttpServletResponse.SC_OK);
        //baseRequest.setHandled(true);
        
        
        
        response.getWriter().println(answer);        
        
        
    }
    
    
    
}
