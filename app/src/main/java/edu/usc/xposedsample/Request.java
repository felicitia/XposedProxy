package edu.usc.xposedsample;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by felicitia on 3/13/17.
 */

public class Request {
    public List<String> subStrings;
    public int nodeId;
    int unknownCount;

    public Request(){
        subStrings = new ArrayList<String>();
    }

    public void addSubString(String str){
        subStrings.add(str);
    }
}
