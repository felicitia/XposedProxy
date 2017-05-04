package edu.usc.xposedsample;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by felicitia on 3/13/17.
 */

public class RequestsPerApp {

    public String pkgName;
    Map<Integer, Request> requestsMap = new HashMap<Integer, Request>();

    //hack for now
    public RequestsPerApp(){
        pkgName = "edu.usc.yixue.weatherapp";
        Request url1 = new Request();
        url1.addSubString("http://api.openweathermap.org/data/2.5/weather?units=Imperial&id=");
        url1.addSubString(null);
        url1.addSubString("&APPID=f46f62442611cdc087b629f6e87c7374");
        url1.unknownCount = 1;
        url1.nodeId = 271;

        Request url2 = new Request();
        url2.addSubString("http://api.openweathermap.org/data/2.5/weather?units=Imperial&id=3882428&APPID=f46f62442611cdc087b629f6e87c7374");
        url2.unknownCount = 0;
        url2.nodeId = 275;

        Request url3 = new Request();
        url3.addSubString("http://api.openweathermap.org/data/2.5/weather?units=Imperial&q=");
        url3.addSubString(null);
        url3.addSubString("&APPID=f46f62442611cdc087b629f6e87c7374");
        url3.unknownCount = 1;
        url3.nodeId = 269;

        requestsMap.put(url1.nodeId, url1);
        requestsMap.put(url2.nodeId, url2);
        requestsMap.put(url3.nodeId, url3);
    }
}
