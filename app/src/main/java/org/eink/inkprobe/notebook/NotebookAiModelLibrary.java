package org.eink.inkprobe.notebook;

import java.io.IOException;
import java.util.*;
import org.json.*;

/** A bounded, credential-free selection of model profiles; callers scope it to one provider endpoint. */
final class NotebookAiModelLibrary {
    static final int MAX_MODELS=64;
    static List<NotebookAiModel> decode(String encoded) {
        List<NotebookAiModel> values=new ArrayList<>();
        if(encoded.length()>256*1024)return values;
        try{JSONArray data=new JSONArray(encoded);for(int i=0;i<Math.min(MAX_MODELS,data.length());i++){
            try{NotebookAiModel value=NotebookAiModel.stored(data.getJSONObject(i));if(find(values,value.id)==null)values.add(value);}
            catch(JSONException|IllegalArgumentException ignored){/* An invalid record cannot configure a request. */}
        }}catch(JSONException ignored){}
        return values;
    }
    static String encode(List<NotebookAiModel> models)throws IOException {
        if(models.size()>MAX_MODELS)throw new IOException("Keep up to 64 saved models per endpoint.");
        try{JSONArray data=new JSONArray();Set<String> ids=new HashSet<>();for(NotebookAiModel model:models){if(!ids.add(model.id))throw new IOException("Duplicate saved model");data.put(model.json());}String value=data.toString();if(value.length()>256*1024)throw new IOException("Saved model metadata exceeds its size limit");return value;}
        catch(JSONException error){throw new IOException("Could not save model metadata",error);}
    }
    static NotebookAiModel find(List<NotebookAiModel> models,String id){for(NotebookAiModel model:models)if(model.id.equals(id))return model;return null;}
    static List<NotebookAiModel> merge(List<NotebookAiModel> before,List<NotebookAiModel> added)throws IOException {
        LinkedHashMap<String,NotebookAiModel> values=new LinkedHashMap<>();for(NotebookAiModel model:before)values.put(model.id,model);
        for(NotebookAiModel model:added)values.put(model.id,model);
        List<NotebookAiModel> result=new ArrayList<>(values.values());encode(result);return result;
    }
}
