package net.shrimpworks.bbc.recipes.recipes;

import java.util.List;
import java.util.Set;

public class Recipe {

	public String id;
	public String title;
	public String description;
	public String image;
	public Set<String> keywords;

	public RecipeSource source;
	public RecipeMeta meta;

	public List<RecipeIngredients> ingredients;
	public RecipeMethod method;

	public static class RecipeSource {

		public String author;
		public String from;
	}

	public static class RecipeMeta {

		public String prepTime;
		public String cookTime;
		public String servings;

		public Set<String> diets;
	}

	public static class RecipeIngredients {

		public String title;
		public List<String> ingredients;
	}

	public static class RecipeMethod {

		public List<String> steps;
		public String tips;
	}

}
