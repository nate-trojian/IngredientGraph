package com.ntrojian.ingredientgraph;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Nate
 * Created on: 7/24/17
 * Description: Loader for Neo4j - Uses BatchInserter so really is just local only
 */
public class Neo4jLoader {
    //TODO Look backs are probably needed
    //TODO Need to redo how inch-[thick|wide|long] is handled

    private final String[] continueSet = new String[] {
        ".*:.*", "ingredient info", "special", "\\*", "garnish", "note", "n/a"
    };
    private final String[] rematchSet = new String[] {"plus", "to", "or"};

    private final String[] measurementSet = new String[] {
        "head", "gallon", "quart", "pint", "cup", "ounce", "tablespoon", "teaspoon", "small", "medium", "large", "pound", "lb", "stalk",
        "clove", "bunch", "sprig", "sheet", "whole", "pinch", "dash", "package", "can", "jar", "carton", "chunk", "block",
        "inch[\\s-]thick", "inch[\\s-]wide", "inch[\\s-]long", "inch[\\s-]cubed?", "inch[\\s-]diced?", "inch[\\s-]piece"
    };

    private final Pattern ingredPattern = Pattern.compile(
            "((?<quant>\\d(?:(?=/)/\\d|(?:(?=\\s\\d)\\s\\d/\\d|\\d*)))[\\s-])?" +
            "((?<measurement>" + regexGroup(measurementSet) + "(?>es|s)?)?" +
            "(?> of|-sized| package | block of |)?[\\s-])?(?<name>.+)"
    );

    private final Pattern continueKeywords = Pattern.compile(
        "^" + regexGroup(continueSet)
    );
    private final Pattern rematchKeywords = Pattern.compile(
        "^" + regexGroup(rematchSet) + " "
    );

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
        props.put("name", recipe.title.trim());

        ArrayList<String> categories = recipe.getCategories();
        Label[] labels = new Label[categories.size()+1];
        for(int i=0; i<categories.size(); i++) {
            labels[i] = Label.label(categories.get(i));
        }
        labels[categories.size()] = Label.label("Recipe");

        Long ret = bi.createNode(props, labels), ingredId;
        String shortName, actualIngred;
        Matcher m;
        StringBuilder sb;
        for(String ingred: recipe.getIngredients()) {
            shortName = "";
            //Need to replace all the numbers in word form to digit form
            //We have 1-10, 11, 12 (needs dozen), 14, 15, 16, 18, 20, 24, 28, 36, 65
            //Also have to replace half, third, quarter
            //Just gonna hard code
            shortName = ingred.toLowerCase();
            shortName = shortName.replaceAll("^(about|approximately|a|use) ", "");
            //shortName.replaceAll("^one| one ")

            m = ingredPattern.matcher(shortName);
            if(m.find()) shortName = m.group("name");

            sb = new StringBuilder();
            boolean inPar = false;
            for(char c: shortName.toCharArray()) {
                // I think continue is technically faster than fall-through
                if(inPar) {
                    if(c == ')') inPar = false;
                }
                else if(c == '(') inPar = true;
                else if(c == ',' || c == ';') break;
                else sb.append(c);
            }
            //Cause this is faster than doing the logic in loop
            actualIngred = sb.toString().trim();

            //SPECIAL CASES TIME
            // ^ Not sure why I'm so excited about it
            m = rematchKeywords.matcher(actualIngred);
            if(m.find()) {
                Matcher rematch = ingredPattern.matcher(actualIngred.substring(m.end()+1));
                if(rematch.find()) actualIngred = rematch.group("name");
            }

            //Yes I meant for this to be here instead of above the preceding if statement
            if(actualIngred.isEmpty()) continue;
            m = continueKeywords.matcher(actualIngred);
            if(m.find()) continue;
            
            actualIngred = actualIngred.replaceAll("\\*", "").trim();

            //We got something
            ingredId = ingredients.get(actualIngred);
            if(ingredId == null) ingredId = addNewIngredient(actualIngred);
            bi.createRelationship(ret, ingredId,
                    RelationshipType.withName("inRecipe"), new HashMap<>());
        }
        return ret;
    }

    private String regexGroup(String[] group) {
        StringBuilder ret = new StringBuilder();
        ret.append("(").append(group[0]);
        for(int i=1; i<group.length; i++) {
            ret.append("|").append(group[i]);
        }
        return ret.append(")").toString();
    }
}
