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
    private final Pattern ingredPattern = Pattern.compile(
            "((?<quant>\\d(?:(?=/)/\\d|(?:(?=\\s\\d)\\s\\d/\\d|\\d*)))\\s)?" +
            "(?<measurement>(head|gallon|quart|pint|cup|ounce|tablespoon|teaspoon|" +
            "small|medium|large|pound|lb|stalk|clove|bunch|sprig|sheet|whole|pinch|dash|" +
            "inch[\\s-]thick|inch[\\s-]wide|inch[\\s-]long)(?>es|s)?)?(?> of|-sized)?[\\s-]?" +
            "(?<name>.+)"
    );
    private final Pattern amountPattern = Pattern.compile(
            "(?<quant>\\d(?:(?=/)/\\d|(?:(?=\\s\\d)\\s\\d/\\d|\\d*)))" +
            "(?<measurement>(head|gallon|quart|pint|cup|ounce|tablespoon|teaspoon|\" +\n" +
            "small|medium|large|pound|lb|stalk|clove|bunch|sprig|sheet|whole|pinch|dash|\" +\n" +
            "inch-thick|inch-wide|inch-long)(?>es|s)?)?(?> of|-sized)"
    );

    /* Cypher to create related edges
    MATCH (i1:Ingredient)--(r:`22-Minute Meals`)--(i2:Ingredient) WHERE NOT i1=i2 WITH i1, i2, count(DISTINCT r) as comRec
    MATCH (r:`22-Minute Meals`) WITH i1, i2, comRec, count(r) as numRec
    MATCH (i1)--(r:`22-Minute Meals`) WITH i1, i2, comRec, numRec, count(r) as i1Rec
    WITH i1, i2, comRec/toFloat(i1Rec) as weight
    WHERE weight > 0.7
    CREATE (i1)-[:relatesTo {weight: weight}]->(i2)
     */

    /* Cypher get density of graph
    MATCH (i1:Ingredient)-[r1:relatesTo]-(i2:Ingredient)-[r2:relatesTo]-(i1) WITH i1, sum((r1.weight+r2.weight)/2) as weightedCon, count(i2) as unweightedCon
    MATCH (i1:Ingredient)-[r:relatesTo]-(:Ingredient) WITH i1, weightedCon, unweightedCon, toFloat(count(r)) as numR
    WITH i1, weightedCon/numR as weightedDensity, unweightedCon/numR as unweightedDensity
    WITH i1, weightedDensity, unweightedDensity, (unweightedDensity - weightedDensity) as weightShift
    order by weightShift DESC, unweightedDensity DESC
    return i1, weightShift, unweightedDensity, weightedDensity
     */

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

        Long ret = bi.createNode(props, labels), ingredId;
        String shortName, actualIngred;
        Matcher m;
        StringBuilder sb;
        for(String ingred: recipe.getIngredients()) {
            shortName = "";
            m = ingredPattern.matcher(ingred.toLowerCase());
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
            actualIngred = sb.toString();

            if(actualIngred.startsWith("plus ")) {
                m = amountPattern.matcher(actualIngred);
                if(m.find()) {
                    //TODO Save regex groups to a new edge
                    actualIngred = actualIngred.substring(m.end()+1);
                }
            }

            if(actualIngred.isEmpty()) continue;
            ingredId = ingredients.get(actualIngred);
            if(ingredId == null) ingredId = addNewIngredient(actualIngred);
            bi.createRelationship(ret, ingredId,
                    RelationshipType.withName("inRecipe"), new HashMap<>());
        }
        return ret;
    }
}
