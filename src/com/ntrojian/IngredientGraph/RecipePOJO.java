package com.ntrojian.IngredientGraph;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;

/**
 * Author: Nate
 * Created on: 7/24/17
 * Description: Recipe POJO
 */
@JsonIgnoreProperties({"directions", "fat", "date", "calories",
        "desc", "protein", "rating", "sodium"})
public class RecipePOJO {
    /* We can add more things in here
     * But as a PoC, these are the 2 things we need
     */

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String title;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    ArrayList<String> ingredients;

    @JsonInclude(JsonInclude.Include.ALWAYS)
    ArrayList<String> categories;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ArrayList<String> getIngredients() {
        return ingredients;
    }

    public void setIngredients(ArrayList<String> ingredients) {
        this.ingredients = ingredients;
    }

    public ArrayList<String> getCategories() {
        return categories;
    }

    public void setCategories(ArrayList<String> categories) {
        this.categories = categories;
    }

    @Override
    public String toString() {
        return title;
    }
}
