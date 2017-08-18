package edu.usc.xposedsample;

import android.os.AsyncTask;
import android.util.Base64;
import android.util.Log;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/**
 * Created by felicitia on 3/7/17.
 */

public class Proxy implements IXposedHookLoadPackage{
    final private static String unknownCountKey = "unknownCount";
    final private static String subStrKey = "subStrings";
    final private static String instanceKey = "instances";
    final private static int Prefetch_GETINPUTSTREAM = 0;
    final private static int Prefetch_GETRESPONSECODE = 1;
    final private static int Prefetch_GETCONTENT = 2;
    final private static int Prefetch_OPENSTREAM = 3;
    final private static String WAIT_FLAG = "WAIT";
    final private static int CONCURRENT_REQUEST_THRESHOLD = 5;
    private static int concurrentReq = 0;
    private static Map<String, CountDownLatch> countDownLatchMap = new HashMap<String, CountDownLatch>(); //maintain latch for each URL
    static JSONObject responseMap = null; //maintain the responsesResult in the memory instead of file b/c the original app may not have storage access permissions
    static JSONObject requestMap = null; //maintain the requestMap in the memory
    static String initialRequestMapStr = "{\"32745\":{\"subStrings\":[\"https://www.newsblur.com/media/img/reader/default_profile_photo.png\"],\"unknownCount\":0,\"instances\":[\"https://www.newsblur.com/media/img/reader/default_profile_photo.png\"]}}";
    static int timestampCounter = 0;


    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
//        if(loadPackageParam.packageName.equals(pkgName))
        {
            /**
             *  Replace: usc.yixue.Proxy.sendDef(String body, String sig, String value, String nodeId, int index, String pkgName)
             *  need to update requestMap value for different apps in this method
             */
            findAndHookMethod("usc.yixue.Proxy", loadPackageParam.classLoader, "sendDef", String.class, String.class, String.class, String.class, int.class, String.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            if(param == null || param.args[0] == null || param.args[1] == null || param.args[2] == null || param.args[3] == null || param.args[4] == null || param.args[5] == null ){return null;}
                            String body = param.args[0].toString();
                            String sig = param.args[1].toString();
                            String value = param.args[2].toString();
                            String nodeId = param.args[3].toString();
                            int index = (int)param.args[4];
                            String pkgName = param.args[5].toString();
                            Log.e("sendDef()", body+"\t"+sig+"\t"+value+"\t"+nodeId+"\t"+index+"\t"+pkgName);
                            if(value == null || value == ""){
                                return null;
                            }
                            if(requestMap == null){
                                JSONParser parser = new JSONParser();
                                Object obj = parser.parse(initialRequestMapStr);
                                requestMap = (JSONObject) obj;

                            }
                            JSONObject node = (JSONObject) requestMap.get(nodeId);
                            if(node != null){
                                Log.e("node in sendDef()", node.toJSONString());
                                JSONArray subStrings = (JSONArray) node.get(subStrKey);
                                int count = Integer.parseInt(node.get(unknownCountKey).toString());
                                //find the one that's being sent
                                if(subStrings.get(index).equals("null")){
                                    subStrings.set(index, value);
                                    count--;
                                    node.put(unknownCountKey, count);
//                                    //if the whole URL is known, then prefetch
//                                    if(count == 0){
//                                        prefetchNode(node, Prefetch_OPENSTREAM);
//                                    }
                                }else {
                                    // if the substring is not null, only do the update, don't reduce the unknowncount
                                    subStrings.set(index, value);
                                }
                                if(0 == count){
                                    JSONArray instanceArray = (JSONArray)node.get(instanceKey);
                                    instanceArray.add(getWholeUrlString(node));
//                                    prefetchNode(node, Prefetch_OPENSTREAM);
                                }
                                Log.e("node after sendDef()", node.toJSONString());
                            }
                            return null;
                        }
                    }
            );

            /**
             * Replace: usc.yixue.Proxy.getInputStream(URLConnection urlConn)
             *
             */
            findAndHookMethod("usc.yixue.Proxy", loadPackageParam.classLoader, "getInputStream", URLConnection.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            URLConnection urlConn = (URLConnection) param.args[0];
                            String urlStr = urlConn.getURL().toString();
                            Log.e("getInputStream()", urlStr);
                                if(responseMap == null){
                                    responseMap = new JSONObject();
                                }
                                Log.e("responseMap", responseMap.toJSONString());
                                //already have response ready for the specific url
                                if(responseMap.containsKey(urlStr)){
                                    // wait for the prefetch to return the response
                                    Log.e("responseMap", "yup\t"+urlStr);
                                    if(responseMap.get(urlStr).equals(WAIT_FLAG)){
                                        Log.e("responseMap", "wait\t"+urlStr);
                                        CountDownLatch countDownLatch = new CountDownLatch(1);
                                        countDownLatch.await();
                                        countDownLatchMap.put(urlStr, countDownLatch);
                                    }
                                    byte[] byteResult = Base64.decode(responseMap.get(urlStr).toString(), Base64.DEFAULT);
                                    return new ByteArrayInputStream(byteResult);
                                }else {
                                    Log.e("responseMap", "nope");
                                    final InputStream inputStream = urlConn.getInputStream();
                                    byte[] byteResult = getbytesFromInputStream(inputStream);
                                    if(byteResult != null){
                                        responseMap.put(urlStr, Base64.encodeToString(byteResult, Base64.DEFAULT));
                                    }
                                    return new ByteArrayInputStream(byteResult);
                                }
//                            }
                        }
                    }
            );

            /**
             * Replace: usc.yixue.Proxy.getResponseCode(HttpURLConnection urlConn)
             *
             */
            findAndHookMethod("usc.yixue.Proxy", loadPackageParam.classLoader, "getResponseCode", HttpURLConnection.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            HttpURLConnection urlConn = (HttpURLConnection) param.args[0];
                            String urlStr = urlConn.getURL().toString();
                            Log.e("getResponseCode()", urlStr);
                            if(responseMap == null){
                                responseMap = new JSONObject();
                            }
                            Log.e("responseMap", responseMap.toJSONString());
                            //already have response ready for the specific url
                            if(responseMap.containsKey(urlStr)){
                                Log.e("responseMap", "yup\t"+urlStr);
                                // wait for the prefetch to return the response
                                if(responseMap.get(urlStr).equals(WAIT_FLAG)){
                                    Log.e("responseMap", "wait\t"+urlStr);
                                    CountDownLatch countDownLatch = new CountDownLatch(1);
                                    countDownLatch.await();
                                    countDownLatchMap.put(urlStr, countDownLatch);
                                }
                                int cachedResponseCode = (int)responseMap.get(urlStr);
                                return cachedResponseCode;
                            }else {
                                Log.e("responseMap", "nope\t"+urlStr);
                                final int responseCode = urlConn.getResponseCode();
                                responseMap.put(urlStr, responseCode);
                                return responseCode;
                            }
                        }
                    }
            );

            /**
             * Replace: usc.yixue.Proxy.getContent(URLConnection urlConn)
             *
             */
            findAndHookMethod("usc.yixue.Proxy", loadPackageParam.classLoader, "getContent", URLConnection.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            URLConnection urlConn = (URLConnection) param.args[0];
                            String urlStr = urlConn.getURL().toString();
                            Log.e("getContent()", urlStr);
                            if(responseMap == null){
                                responseMap = new JSONObject();
                            }
                            Log.e("responseMap", responseMap.toJSONString());
                            //already have response ready for the specific url
                            if(responseMap.containsKey(urlStr)){
                                Log.e("responseMap", "yup");
                                // wait for the prefetch to return the response
                                if(responseMap.get(urlStr).equals(WAIT_FLAG)){
                                    Log.e("responseMap", "wait\t"+urlStr);
                                    CountDownLatch countDownLatch = new CountDownLatch(1);
                                    countDownLatch.await();
                                    countDownLatchMap.put(urlStr, countDownLatch);
                                }
                                byte[] byteResult = Base64.decode(responseMap.get(urlStr).toString(), Base64.DEFAULT);
//                                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteResult);
//                                ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
//                                return objectInputStream.readObject();
                                return new ByteArrayInputStream(byteResult);
                            }else {
                                Log.e("responseMap", "nope");
                                final Object object = urlConn.getContent();
                                InputStream inputStream = (InputStream) object;
                                byte[] byteResult = getbytesFromInputStream(inputStream);
                                if(byteResult != null){
                                    responseMap.put(urlStr, Base64.encodeToString(byteResult, Base64.DEFAULT));
                                }
//                                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteResult);
//                                ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
//                                return objectInputStream.readObject();
                                return new ByteArrayInputStream(byteResult);
                            }
//                            }
                        }
                    }
            );

            /**
             * Replace: usc.yixue.Proxy.openStream(URL url)
             *
             */
            findAndHookMethod("usc.yixue.Proxy", loadPackageParam.classLoader, "openStream", URL.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            URL url = (URL) param.args[0];
                            String urlStr = url.toString();
                            Log.e("openStream()", urlStr);
                            if(responseMap == null){
                                responseMap = new JSONObject();
                            }
                            Log.e("responseMap", responseMap.toJSONString());
                            //already have response ready for the specific url
                            if(responseMap.containsKey(urlStr)){
                                Log.e("responseMap", "yup");
                                Log.e("value", responseMap.get(urlStr).toString());
                                // wait for the prefetch to return the response
                                if(responseMap.get(urlStr).equals(WAIT_FLAG)){
                                    Log.e("responseMap", "wait\t"+urlStr);
                                    CountDownLatch countDownLatch = new CountDownLatch(1);
                                    if(countDownLatchMap.get(urlStr) == null){
                                        countDownLatchMap.put(urlStr, countDownLatch);
                                    }
                                    countDownLatch.await();
                                }
                                byte[] byteResult = Base64.decode(responseMap.get(urlStr).toString(), Base64.DEFAULT);
                                return new ByteArrayInputStream(byteResult);
                            }else {
                                Log.e("responseMap", "nope");
                                final InputStream inputStream = url.openStream();
                                byte[] byteResult = getbytesFromInputStream(inputStream);
                                if(byteResult != null){
                                    responseMap.put(urlStr, Base64.encodeToString(byteResult, Base64.DEFAULT));
                                }
                                return new ByteArrayInputStream(byteResult);
                            }
//                            }
                        }
                    }
            );


            /**
             * Replace: usc.yixue.Proxy.triggerPrefetch(String body, String sig, String nodeIds), this method is inserted before every return statement in each trigger method
             *
             */
            findAndHookMethod("usc.yixue.Proxy", loadPackageParam.classLoader, "triggerPrefetch", String.class, String.class, String.class, int.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            String body = param.args[0].toString();
                            String sig = param.args[1].toString();
                            String paramValue = param.args[2].toString();
                            int Prefetch_Method = (int) param.args[3];
                            Log.e("triggerPrefetch()", body+"\t"+sig+"\t"+paramValue);
                            String[] nodeIds = paramValue.split("@");
                            if(requestMap == null){
                                JSONParser parser = new JSONParser();
                                Object obj = parser.parse(initialRequestMapStr);
                                requestMap = (JSONObject) obj;
//                                Log.e("requestMap in null", requestMap.toJSONString());
                            }
                            for(String nodeId: nodeIds){
//                                Log.e("nodeId", nodeId);
                                Log.e("requestMap", requestMap.toJSONString());
                                JSONObject node = (JSONObject) requestMap.get(nodeId);
                                Log.e("node in trigger", node.toJSONString());
                                prefetchNode(node, Prefetch_Method);
                            }
                            return null;
                        }
                    }
            );


            /***
             * Replace: usc.yixue.Proxy.printTimeDiff(String body, String sig, long timeDiff) and replace it with Log.e because it's more obvious to see in the logcat
             */
            findAndHookMethod("usc.yixue.Proxy", loadPackageParam.classLoader, "printTimeDiff", String.class, String.class, long.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            String body = param.args[0].toString();
                            String sig = param.args[1].toString();
                            long timeDiff = (long) param.args[2];
//                            if(timeDiff >= 50)
                            {
                                Log.e("printTimeDiff()", timestampCounter + "\t" + body+"\t"+sig+ "\t" +timeDiff);
                                timestampCounter++;
                            }
                            return null;
                        }
                    }
            );

            /***
             * Replace: usc.yixue.Proxy.printUrl(String body, String sig, String url) and replace it with Log.e because it's more obvious to see in the logcat
             */
            findAndHookMethod("usc.yixue.Proxy", loadPackageParam.classLoader, "printUrl", String.class, String.class, String.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            String body = param.args[0].toString();
                            String sig = param.args[1].toString();
                            String url = param.args[2].toString();
                            Log.e("printURL()", body + "\t" + sig+ "\t" +url);

                            return null;
                        }
                    }
            );

        }

    }

    public void executePrefetch(String urlStr, int Prefetch_METHOD){

        switch (Prefetch_METHOD){
            case Prefetch_GETINPUTSTREAM:
                Log.e("executeGetInputTask", "url = "+urlStr);
                PrefetchGetInputStreamTask prefetchGetInputStreamTask = new PrefetchGetInputStreamTask();
                prefetchGetInputStreamTask.execute(urlStr);
                break;
            case Prefetch_GETRESPONSECODE:
                Log.e("executeGetResponseTask", "url = "+urlStr);
                PrefetchGetResponseCodeTask prefetchGetResponseCodeTask = new PrefetchGetResponseCodeTask();
                prefetchGetResponseCodeTask.execute(urlStr);
                break;
            case Prefetch_GETCONTENT:
                Log.e("executeGetContentTask", "url = "+urlStr);
                PrefetchGetContentTask prefetchGetContentTask = new PrefetchGetContentTask();
                prefetchGetContentTask.execute(urlStr);
                break;
            case Prefetch_OPENSTREAM:
                Log.e("executeOpenStreamTask", "url = "+urlStr);
                PrefetchOpenStreamTask openStreamTask = new PrefetchOpenStreamTask();
                openStreamTask.execute(urlStr);
                break;
        }

    }

    /**
     * only if all the sub strings are known && the result is not cached already
     * @param node
     */
    public void prefetchNode(JSONObject node, int Prefetch_METHOD){
        if(node == null){
            return;
        }
        //only prefetch if every substring is known
//        if(0 == Integer.parseInt(node.get(unknownCountKey).toString())){
//            String url = getWholeUrlString(node);
//            if(responseMap == null){
//                responseMap = new JSONObject();
//            }
//            if(!responseMap.containsKey(url)){
//                /**
//                 * set WAIT_FLAG means this request is already prefetched.
//                 * so if the response is not in the cache when the actual request is sent,
//                 * wait for the prefetched response instead of making another request.
//                 */
//                responseMap.put(url, WAIT_FLAG);
//                Log.e("setWaitFlag", responseMap.toJSONString());
//                executePrefetch(url, Prefetch_METHOD);
//            }
//        }
        /**
         * prefetch the constant URLs in "instances"
         */
        JSONArray urlInstances = (JSONArray) node.get(instanceKey);
        if(urlInstances == null){
            return;
        }
        Log.e("instances", urlInstances.toJSONString());
        for(int i=0; i<urlInstances.size(); i++){
            String url = urlInstances.get(i).toString();
            if(responseMap == null){
                responseMap = new JSONObject();
            }
            if(!responseMap.containsKey(url) && concurrentReq <= CONCURRENT_REQUEST_THRESHOLD){
                /**
                 * set WAIT_FLAG means this request is already prefetched.
                 * so if the response is not in the cache when the actual request is sent,
                 * wait for the prefetched response instead of making another request.
                 */
                concurrentReq++;
                responseMap.put(url, WAIT_FLAG);
                Log.e("setWaitFlag", responseMap.toJSONString());
                executePrefetch(url, Prefetch_METHOD);
            }
        }
    }

        private class PrefetchGetInputStreamTask extends AsyncTask<String, Void, Response> {

        @Override
        protected Response doInBackground(String... urlStrs){
            for(String urlStr: urlStrs) {
                try {
                    URL url = new URL(urlStr);
                    Log.e("doInBg", urlStr);
                    Response result = new Response();
                    result.key = urlStr;
                    URLConnection urlConnection = url.openConnection();
                    InputStream inputStream = urlConnection.getInputStream();
                    result.value =  getbytesFromInputStream(inputStream);
                    responseMap.put(result.key, Base64.encodeToString(result.value, Base64.DEFAULT));
                    Log.e("responseMap updates", responseMap.toJSONString());
                    CountDownLatch countDownLatch = countDownLatchMap.get(urlStr);
                    if(countDownLatch != null){
                        countDownLatch.countDown();
                    }
                    inputStream.close();
                    return result;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Log.e("error", "error in PrefetchGetInputStreamTask");
            return null;
        }

            @Override
            protected void onPostExecute(Response response){
                concurrentReq--;
            }

    }

    private class PrefetchOpenStreamTask extends AsyncTask<String, Void, Response> {

        @Override
        protected Response doInBackground(String... urlStrs){
            for(String urlStr: urlStrs) {
                try {
                    URL url = new URL(urlStr);
                    Log.e("doInBg", urlStr);
                    Response result = new Response();
                    result.key = urlStr;
                    InputStream inputStream = url.openStream();
                    result.value =  getbytesFromInputStream(inputStream);
                    responseMap.put(result.key, Base64.encodeToString(result.value, Base64.DEFAULT));
                    Log.e("responseMap updates", responseMap.toJSONString());
                    CountDownLatch countDownLatch = countDownLatchMap.get(urlStr);
                    if(countDownLatch != null){
                        countDownLatch.countDown();
                        countDownLatchMap.remove(urlStr);
                    }
                    inputStream.close();
                    return result;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Log.e("error", "error in PrefetchOpenStreamTask");
            return null;
        }

        @Override
        protected void onPostExecute(Response response){
            concurrentReq--;
        }

    }

    private class PrefetchGetResponseCodeTask extends AsyncTask<String, Void, Response> {

        @Override
        protected Response doInBackground(String... urlStrs){
            for(String urlStr: urlStrs) {
                try {
                    URL url = new URL(urlStr);
                    Log.e("doInBg", urlStr);
                    Response result = new Response();
                    result.key = urlStr;
                    HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
                    int responseCode = urlConnection.getResponseCode();
                    result.value =  getBytesFromInt(responseCode);
                    //if Prefetch_Method is getResponseCode, then just store the int value in the responseMap
                    responseMap.put(result.key, getIntFromBytes(result.value));
                    Log.e("responseMap updates", responseMap.toJSONString());
                    CountDownLatch countDownLatch = countDownLatchMap.get(urlStr);
                    if(countDownLatch != null){
                        countDownLatch.countDown();
                    }
                    urlConnection.disconnect();
                    return result;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Log.e("error", "error in PrefetchGetResponseCodeTask");
            return null;
        }

        @Override
        protected void onPostExecute(Response response){
            concurrentReq--;
        }
    }

    private class PrefetchGetContentTask extends AsyncTask<String, Void, Response> {

        @Override
        protected Response doInBackground(String... urlStrs){
            for(String urlStr: urlStrs) {
                try {
                    URL url = new URL(urlStr);
                    Log.e("doInBg", urlStr);
                    Response result = new Response();
                    result.key = urlStr;
                    URLConnection urlConnection = url.openConnection();
                    Object object = urlConnection.getContent();
                    InputStream inputStream = (InputStream) object;
                    result.value =  getbytesFromInputStream(inputStream);
                    responseMap.put(result.key, Base64.encodeToString(result.value, Base64.DEFAULT));
                    Log.e("responseMap updates", responseMap.toJSONString());
                    CountDownLatch countDownLatch = countDownLatchMap.get(urlStr);
                    if(countDownLatch != null){
                        countDownLatch.countDown();
                    }
                    return result;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Log.e("error", "error in PrefetchGetContentTask");
            return null;
        }

        @Override
        protected void onPostExecute(Response response){
            concurrentReq--;
        }

    }

    static String getWholeUrlString(JSONObject node){
        JSONArray subStrings = (JSONArray) node.get(subStrKey);
        StringBuilder url = new StringBuilder();
        for(int i=0; i<subStrings.size(); i++){
            url.append(subStrings.get(i));
        }
        return url.toString();
    }

    static byte[] getbytesFromInputStream(InputStream is) throws IOException {
        int nRead;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        byte[] data = new byte[1024];

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();

        return buffer.toByteArray();

    }

    static  byte[] getBytesFromInt(int value){
        return  ByteBuffer.allocate(4).putInt(value).array();
    }

    static int getIntFromBytes(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    static byte[] getBytesFromObject(Object obj){
        ByteArrayOutputStream bos = null;
        ObjectOutput out = null;
        byte[] bytes = null;
        try {
            bos = new ByteArrayOutputStream();
            out = new ObjectOutputStream(bos);
            out.writeObject(obj);
            out.flush();
            bytes = bos.toByteArray();
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }finally{
            try{
            bos.flush();
            bos.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
        return bytes;
    }

    private class Response{
        public String key;
        public byte[] value;
    }

}
