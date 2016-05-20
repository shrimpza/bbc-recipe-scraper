package net.shrimpworks.bbc.recipes.recipes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.shrimpworks.bbc.recipes.ScraperTask;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class RecipeScraper implements ScraperTask {

	private static ObjectMapper JSONMAPPER = new ObjectMapper();

	private static final String RECIPE_URL = "/recipes/%s";

	private final String id;
	private final ExecutorService executor;

	private final String url;
	private final String dir;

	public RecipeScraper(String dataPath, String rootUrl, String id, ExecutorService executor) {
		this.id = id;
		this.executor = executor;
		this.url = rootUrl + String.format(RECIPE_URL, id);

		// make the paths we need
		Path path = Paths.get(dataPath, id);
		try {
			if (!Files.exists(path)) Files.createDirectories(path);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		this.dir = path.toString() + File.separator;
	}

	@Override
	public void execute(Connection connection) {
		try {
			Document doc = connection.url(url).get();
			Element el;

			Recipe recipe = new Recipe();

			// recipe basics
			recipe.id = id;
			recipe.title = doc.select(".recipe-main-info h1.content-title__text").first().text();

			// description - occasionally missing
			recipe.description = optional(doc,
										  ".recipe-main-info .recipe-description"); // TODO this strips formatting which relied on newlines being formed by <br/>s - maybe strip all non-br tags, then replace brs with newlines

			// find and download the image - if no image is found, it's probably a video, in which case also see if there's a placeholder image
			el = doc.select(".recipe-media img.recipe-media__image").first();
			if (el == null) el = doc.select(".recipe-media .emp-placeholder img").first();
			recipe.image = el == null ? null : downloadImage(el.absUrl("src"), dir);

			// keywords, pulled from "related" recipe links
			recipe.keywords = doc.select(".related-recipes__content span.related-recipes__more-name").stream()
								 .map(Element::text)
								 .map(k -> k.replaceAll(" recipes", ""))
								 .collect(Collectors.toSet());

			// general recipe information
			recipe.meta = new Recipe.RecipeMeta();
			recipe.meta.prepTime = optional(doc, ".recipe-main-info .recipe-metadata__prep-time");
			recipe.meta.cookTime = optional(doc, ".recipe-main-info .recipe-metadata__cook-time");
			recipe.meta.servings = optional(doc, ".recipe-main-info .recipe-metadata__serving");

			// this only ever seems to include vegetarian information, though we'll treat is as a list just in case
			recipe.meta.diets = doc.select(".recipe-metadata__dietary a p").stream()
								   .map(Element::text)
								   .collect(Collectors.toSet());

			// try to get the recipe author, and the name of the program it came from
			recipe.source = new Recipe.RecipeSource();
			recipe.source.author = optional(doc, ".recipe-chef .chef__name a.chef__link");
			recipe.source.from = optional(doc, ".recipe-chef .chef__programme-name a.chef__link");

			// recipe method list - tips are sometimes present as well, they might be useful to store
			recipe.method = new Recipe.RecipeMethod();
			recipe.method.steps = doc.select(".recipe-method li.recipe-method__list-item").stream()
									 .map(Element::text)
									 .collect(Collectors.toList());
			recipe.method.tips = optional(doc, "#recipe-tips .recipe-tips__text"); // TODO don't lose newlines, as per description above

			// ingredients - a list which may be grouped under a single heading, or may have sub-headings with additional ingredients lists
			// the DOM may look like one of the following: "<h2/><ol/>", "<h2/><ol/><h3/><ol/>...", "<h2/><h3/><ol/>..."
			// attempt to get the "main" ingredients list (one not in any sub-group)
			recipe.ingredients = new ArrayList<>();
			Element mainIngredients = doc.select(".recipe-ingredients-wrapper .recipe-ingredients__heading").first();
			if (mainIngredients.nextElementSibling().tagName().equals("ul")) {
				Recipe.RecipeIngredients ingredients = new Recipe.RecipeIngredients();
				ingredients.title = mainIngredients.text();
				ingredients.ingredients = mainIngredients.nextElementSibling().getElementsByTag("li").stream()
														 .map(Element::text)
														 .collect(Collectors.toList());
				recipe.ingredients.add(ingredients);
			}

			// get ingredients which fall under their own headings
			Elements ingredientGroups = doc.select(".recipe-ingredients-wrapper .recipe-ingredients__sub-heading");
			if (!ingredientGroups.isEmpty()) {
				recipe.ingredients.addAll(ingredientGroups.stream()
														  .map(e -> {
															  Recipe.RecipeIngredients l = new Recipe.RecipeIngredients();
															  l.title = e.text();
															  l.ingredients = e.nextElementSibling().getElementsByTag("li").stream()
																			   .map(Element::text)
																			   .collect(Collectors.toList());
															  return l;
														  }).collect(Collectors.toList()));
			}

			// store the recipe
			JSONMAPPER.writeValue(Files.newOutputStream(Paths.get(this.dir, id + ".json")), recipe);
		} catch (IllegalArgumentException e) {
			// TODO REVIEW this is a giant hack, because randomly jsoup will throw some assertion errors during connection.url().get().
			System.out.println("#### Failed to process recipe id " + id + " to to weirdness, resubmitting");
			executor.submit(() -> this.execute(connection));
			e.printStackTrace();
		} catch (RuntimeException e) {
			System.out.println("#### Failed to process recipe id " + id);
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String optional(Document document, String selector) {
		Element el = document.select(selector).first();
		return el == null ? null : el.text();
	}

	private String downloadImage(String url, String outPath) throws IOException {
		String name = url.substring(url.lastIndexOf("/") + 1);

		// open a URL Stream
		Connection.Response resultImageResponse = Jsoup.connect(url).ignoreContentType(true).execute();

		// write to file
		Files.write(Paths.get(outPath, name), resultImageResponse.bodyAsBytes());

		return name;
	}
}
