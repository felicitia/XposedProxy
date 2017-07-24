package edu.usc.xposedsample;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

/**
 * Created by felicitia on 3/7/17.
 */

public class Proxy implements IXposedHookLoadPackage{
    final public static String unknownCountKey = "unknownCount";
    final public static String subStrKey = "subStrings";
//    final public static String responseMapPath = "/sdcard/responsesMap.json";
    final public static String weatherPkg = "edu.usc.yixue.weatherapp";
    static JSONObject jsonResponses = null; //maintain the responsesResult in the memory
    static JSONObject weatherRequest = null; //maintain the substrings of weatherApp in the memory


    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if(loadPackageParam.packageName.equals(weatherPkg)){
            /**
             *  Replace: usc.yixue.Proxy.sendDef(String value, int nodeId, int index, String pkgName)
             *  file path: /sdcard/pkgName.json
             */
            findAndHookMethod("usc.yixue.Proxy", loadPackageParam.classLoader, "sendDef", String.class, String.class, int.class, String.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            String value = param.args[0].toString();
                            String nodeId = param.args[1].toString();
                            int index = (int)param.args[2];
                            String pkgName = param.args[3].toString();
                            Log.e("sendDef", value+"\t"+nodeId+"\t"+index+"\t"+pkgName);

                            if(weatherRequest == null){
                                JSONParser parser = new JSONParser();
                                String requestMapStr = "{\n" +
                                        "  \"755\": {\n" +
                                        "    \"subStrings\": [\n" +
                                        "      \"http://api.openweathermap.org/data/2.5/weather?units=Imperial&id=\",\n" +
                                        "      \"null\",\n" +
                                        "      \"&APPID=f46f62442611cdc087b629f6e87c7374\"\n" +
                                        "    ],\n" +
                                        "    \"unknownCount\": 1\n" +
                                        "  },\n" +
                                        "  \"751\": {\n" +
                                        "    \"subStrings\": [\n" +
                                        "      \"http://api.openweathermap.org/data/2.5/weather?units=Imperial&q=\",\n" +
                                        "      \"null\",\n" +
                                        "      \"&APPID=f46f62442611cdc087b629f6e87c7374\"\n" +
                                        "    ],\n" +
                                        "    \"unknownCount\": 1\n" +
                                        "  },\n" +
                                        "  \"759\": {\n" +
                                        "    \"subStrings\": [\n" +
                                        "      \"http://api.openweathermap.org/data/2.5/weather?units=Imperial&id=\",\n" +
                                        "      \"null\",\n" +
                                        "      \"&APPID=f46f62442611cdc087b629f6e87c7374\"\n" +
                                        "    ],\n" +
                                        "    \"unknownCount\": 1\n" +
                                        "  }\n" +
                                        "}";
                                Object obj = parser.parse(requestMapStr);
                                weatherRequest = (JSONObject) obj;
                                Log.e("requests", weatherRequest.toJSONString());
                            }
                            JSONObject node = (JSONObject)weatherRequest.get(nodeId);
                            Log.e("node", node.toJSONString());
                            JSONArray subStrings = (JSONArray) node.get(subStrKey);
                            //find the one that's being sent
                            if(subStrings.get(index).equals("null")){
                                subStrings.set(index, value);
                                int count = Integer.parseInt(node.get(unknownCountKey).toString());
                                count--;
                                node.put(unknownCountKey, count);
                            }else {
                                // if the substring is not null, only do the update, don't reduce the unknowncount
                                subStrings.set(index, value);
                            }
//                            FileWriter writer = new FileWriter("/sdcard/"+pkgName+".json");
//                            writer.write(weatherRequest.toJSONString());
//                            writer.flush();
//                            writer.close();
                            return null;
                        }
                    }
            );

            /**
             * Replace: usc.yixue.Proxy.getInputStream(URLConnection urlConn)
             * file path: /sdcard/responseMapPath
             */
            findAndHookMethod("usc.yixue.Proxy", loadPackageParam.classLoader, "getInputStream", URLConnection.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            URLConnection urlConn = (URLConnection) param.args[0];
                            String urlStr = urlConn.getURL().toString();
                            Log.e("getInputStream", urlStr);
                            //if responseMap file doesn't exist, create it
//                            File responseFile = new File(responseMapPath);
//                            if(!responseFile.exists()){
//                                responseFile.createNewFile();
//                                return null;
//                            }
//                            //if responseMap file is empty
//                            else if(responseFile.length()==0){
//                                return urlConn.getInputStream();
//                            }
//                            else{
                                if(jsonResponses == null){
//                                    JSONParser parser = new JSONParser();
//                                    jsonResponses = (JSONObject) parser.parse(new FileReader(responseMapPath));
                                    jsonResponses = new JSONObject();
                                }
                                //already have response ready for the specific url
                                if(jsonResponses.containsKey(urlStr)){
                                    Log.e("jsonResponses", "yup");
                                    byte[] byteResult = Base64.decode(jsonResponses.get(urlStr).toString(), Base64.DEFAULT);
                                    return new ByteArrayInputStream(byteResult);
                                }else {
                                    Log.e("jsonResponses", "nope");
                                    final InputStream inputStream = urlConn.getInputStream();
                                    byte[] byteResult = getbytesFromInputStream(inputStream);
                                    if(byteResult != null){
                                        jsonResponses.put(urlStr, Base64.encodeToString(byteResult, Base64.DEFAULT));
//                                        try {
//                                            FileWriter writer = new FileWriter(responseFile.getAbsoluteFile());
//                                            writer.write(jsonResponses.toJSONString());
//                                            writer.flush();
//                                            writer.close();
//                                        } catch (IOException e) {
//                                            e.printStackTrace();
//                                        }
                                    }
                                    return new ByteArrayInputStream(byteResult);
                                }
//                            }
                        }
                    }
            );

            /**
             * Replace: usc.yixue.Proxy.triggerPrefetch(String nodeIds), this method is inserted before every return statement in each trigger method
             * file path: /sdcard/pkgName.json
             */
            findAndHookMethod("usc.yixue.Proxy", loadPackageParam.classLoader, "triggerPrefetch", String.class, new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            String paramValue = param.args[0].toString();
                            Log.e("triggerPrefetch", paramValue);
                            String[] nodeIds = paramValue.split("@");
//                            JSONParser parser = new JSONParser();
//                            Object obj = parser.parse(new FileReader("/sdcard/"+weatherPkg+".json"));
//                            JSONObject requests = (JSONObject) obj;
                            for(String nodeId: nodeIds){
                                JSONObject node = (JSONObject) weatherRequest.get(nodeId);
                                prefetchNode(node);
                            }
                            return null;
                        }
                    }
            );

        }

//        if (loadPackageParam.packageName.equals("this is the old code...")){
//
//
//            findAndHookMethod("edu.usc.yixue.weatherapp.Proxy", loadPackageParam.classLoader, "getResult", String.class, new XC_MethodReplacement() {
//                        @Override
//                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
//                            Log.e("param size: ", ""+param.args.length);
//                            String urlStr = param.args[0].toString();
//                            JSONParser parser = new JSONParser();
//                            jsonResponses = (JSONObject) parser.parse(new FileReader(responseMapPath));
//                            //already have response ready for the specific url
//                            if(jsonResponses.containsKey(urlStr)){
//                                return jsonResponses.get(urlStr).toString();
//                            }else {
//                                executePrefetch(urlStr);
//                            }
//                            return null;
//                        }
//                    }
//            );
//
//            findAndHookMethod("edu.usc.yixue.weatherapp.MainActivity", loadPackageParam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    JSONParser parser = new JSONParser();
//                    Object obj = parser.parse(new FileReader(requestMapPath));
//                    JSONObject jsonObject = (JSONObject) obj;
//                    JSONObject requests = (JSONObject) jsonObject.get("edu.usc.yixue.weatherapp");
//
//                    JSONObject node = (JSONObject) requests.get("" + 269);
//                    prefetchNode(node);
//                    node = (JSONObject) requests.get("" + 272);
//                    prefetchNode(node);
//                    node = (JSONObject) requests.get("" + 275);
//                    prefetchNode(node);
//                }
//            });
//
//            findAndHookMethod("edu.usc.yixue.weatherapp.MainActivity$OnCityNameSelectedListener", loadPackageParam.classLoader, "onItemSelected", AdapterView.class, View.class, int.class, long.class, new XC_MethodHook() {
//                @Override
//                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                    JSONParser parser = new JSONParser();
//                    Object obj = parser.parse(new FileReader(requestMapPath));
//                    JSONObject jsonObject = (JSONObject) obj;
//                    JSONObject requests = (JSONObject) jsonObject.get("edu.usc.yixue.weatherapp");
//
//                    JSONObject node = (JSONObject) requests.get("" + 269);
//                    prefetchNode(node);
//                    node = (JSONObject) requests.get("" + 272);
//                    prefetchNode(node);
//                    node = (JSONObject) requests.get("" + 275);
//                    prefetchNode(node);
//                }
//            });
//        }

        /**
         * com.newsblur: getContent(URLConnection urlConn)
         */
//        if (loadPackageParam.packageName.equals("com.newsblur")){
//            findAndHookMethod("usc.yixue.Proxy", loadPackageParam.classLoader, "getContent", URLConnection.class, new XC_MethodReplacement() {
//                        @Override
//                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
//                            Log.e("param size", ""+param.args.length);
//                            URLConnection urlConn = (URLConnection) param.args[0];
//                            String urlStr = urlConn.getURL().toString();
//                            Log.e("param string", urlStr);
//                            JSONParser parser = new JSONParser();
//                            jsonResponses = (JSONObject) parser.parse(new FileReader(responseMapPath));
////                            //already have response ready for the specific url
//                            if(jsonResponses.containsKey(urlStr)){
//                                Log.e("jsonResponses", "yup");
//                                return jsonResponses.get(urlStr).toString();
//                            }else {
//                                Log.e("jsonResponses", "nope");
//                                final Object result = urlConn.getContent();
//                                byte[] byteResult = getbytesFromObject(result);
//                                if(byteResult != null){
//                                    jsonResponses.put(urlStr, byteResult);
//                                    try {
//                                        FileWriter writer = new FileWriter(responseMapPath);
//                                        writer.write(jsonResponses.toJSONString());
//                                        writer.flush();
//                                        writer.close();
//                                    } catch (IOException e) {
//                                        e.printStackTrace();
//                                    }
//                                }
//                                return result;
//                            }
//                        }
//                    }
//            );
//
//        }

    }

    public void executePrefetch(String urlStr){
        Log.e("execute prefetch", "url = "+urlStr);
        PrefetchTask prefetchTask = new PrefetchTask();
        prefetchTask.execute(urlStr);
    }

    /**
     * only if all the sub strings are known && the result is not cached already
     * @param node
     */
    public void prefetchNode(JSONObject node){
        //only prefetch if every substring is known
        if(0 == Integer.parseInt(node.get(unknownCountKey).toString())){
            JSONArray subStrings = (JSONArray) node.get(subStrKey);
            StringBuilder url = new StringBuilder();
            for(int i=0; i<subStrings.size(); i++){
                url.append(subStrings.get(i));
            }
//            JSONParser parser = new JSONParser();
//            File file = new File(responseMapPath);
//            if(file.exists()){
//                Object obj = parser.parse(new FileReader(responseMapPath));
//                jsonResponses = (JSONObject) obj;
//                if(!jsonResponses.containsKey(url.toString())){
//                    executePrefetch(url.toString());
//                }
//            }else{
                //if responsesMap.json doesn't exist
//                jsonResponses = new JSONObject();
//                executePrefetch(url.toString());
//            }
            if(jsonResponses == null){
                jsonResponses = new JSONObject();
            }
            if(!jsonResponses.containsKey(url.toString())){
                executePrefetch(url.toString());
            }
        }
    }

        private class PrefetchTask extends AsyncTask<String, Void, Response> {

        @Override
        protected Response doInBackground(String... urlStrs){
            for(String urlStr: urlStrs) {
                try {
                    URL url = new URL(urlStr);
                    Response result = new Response();
                    result.key = urlStr;
                    URLConnection urlConnection = url.openConnection();
                    InputStream inputStream = urlConnection.getInputStream();
                    result.value =  getbytesFromInputStream(inputStream);
                    inputStream.close();
                    return result;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            Log.e("error", "error in PrefetchTask");
            return null;
        }

        @Override
        protected void onPostExecute(Response result) {
            if(result != null){
                jsonResponses.put(result.key, Base64.encodeToString(result.value, Base64.DEFAULT));
//                FileWriter writer = null;
//                try {
//                    writer = new FileWriter(responseMapPath);
//                    writer.write(jsonResponses.toJSONString());
//                    writer.flush();
//                    writer.close();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
            }

        }
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

    static byte[] getbytesFromObject(Object obj){
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
