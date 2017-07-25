package com.ntrojian.IngredientGraph;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.unsafe.batchinsert.BatchInserter;
import org.neo4j.unsafe.batchinsert.BatchInserters;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Author: Nate
 * Created on: 7/24/17
 * Description: Loader for Neo4j - Uses BatchInserter so really is just local only
 */
public class Neo4jLoader {
    public static void main(String[] args) {
        Neo4jLoader loader = new Neo4jLoader(args[0]);

        JsonFactory f = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper();
        try(JsonParser p = f.createParser(new File(args[1]))) {
            /* Inspired by:
             * http://wiki.fasterxml.com/JacksonInFiveMinutes#Streaming_API_Example_2:_arrays
             */
            p.nextToken();
            while (p.nextToken() == JsonToken.START_OBJECT) {
                //Use mapper as validation on JSON
                RecipePOJO newObj = mapper.readValue(p, RecipePOJO.class);
                //Extra validation cause why not
                if(newObj.getTitle() == null || newObj.getIngredients().size() == 0) continue;
                long newRecipe = loader.addNewRecipe(newObj);
                System.out.println("New recipe added " + newObj);
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
        loader.close();
    }

    private BatchInserter bi;
    private HashMap<String, Long> ingredients;

    public Neo4jLoader(String path) {
        try {
            System.out.println("----Opening Neo4j Connection----");
            bi = BatchInserters.inserter(new File(path));
            ingredients = new HashMap<>();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        System.out.println("----Closing Neo4j Connection----");
        bi.shutdown();
    }

    public long addNewIngredient(String name) {
        HashMap<String, Object> props = new HashMap<>();
        props.put("name", name);
        long ret =  bi.createNode(props, Label.label("Ingredient"));
        ingredients.put(name, ret);
        return ret;
    }

    public long addNewRecipe(RecipePOJO recipe) {
        HashMap<String, Object> props = new HashMap<>();
        props.put("name", recipe.title);

        ArrayList<String> categories = recipe.getCategories();
        Label[] labels = new Label[categories.size()+1];
        for(int i=0; i<categories.size(); i++) {
            labels[i] = Label.label(categories.get(i));
        }
        labels[categories.size()] = Label.label("Recipe");

        long ret = bi.createNode(props, labels);
        for(String ingred: recipe.getIngredients()) {
            String actualIngred = removeFirst2(ingred);
            long ingredId = ingredients.getOrDefault(actualIngred, addNewIngredient(actualIngred));
            bi.createRelationship(ret, ingredId,
                    RelationshipType.withName("inRecipe"), new HashMap<>());
        }
        return ret;
    }

    private String removeFirst2(String name) {
        String[] parts = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for(int i=2; i < parts.length-1; i++) {
            sb.append(parts[i]).append(" ");
        }
        sb.append(parts[parts.length-1]);
        return sb.toString();
    }
}
