package com.cgengine.model;
import java.util.*;
/** Non-UI source of truth for model membership and selected model. */
public final class SceneState {
 private final List<Model> models=new ArrayList<>(); private Model selected;
 public List<Model> models(){return Collections.unmodifiableList(models);} public Model selectedModel(){return selected;}
 public void addModel(Model model){models.add(Objects.requireNonNull(model));selected=model;} public boolean select(Model model){if(indexOf(model)<0)return false;selected=model;return true;}
 public boolean removeModel(Model model){int index=indexOf(model);if(index<0)return false;models.remove(index);if(model==selected)selected=models.isEmpty()?null:models.get(Math.min(index,models.size()-1));return true;}
 private int indexOf(Model model){for(int i=0;i<models.size();i++)if(models.get(i)==model)return i;return -1;}
}
