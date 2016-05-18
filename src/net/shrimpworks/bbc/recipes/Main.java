package net.shrimpworks.bbc.recipes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Main {

	private static final String ROOT = "http://www.bbc.co.uk/food";

	private static final int THREADS = 6;

	private static ObjectMapper JSONMAPPER = new ObjectMapper();

	public static void main(String... args) throws IOException {

		final String dataPath = args[0];

		Connection connection = Jsoup.connect(ROOT);

		// task queue, to be processed by threads
		final Queue<ScraperTask> tasks = new LinkedBlockingQueue<>();

		// for letters a-z, create index scrapers, which will create yet more scrapers
		for (int i = 97; i <= 122; i++) {
			tasks.add(new DishIndex(dataPath, connection, ROOT, (char)i, tasks));
		}

//		tasks.add(new RecipeScraper(dataPath, connection, ROOT, "cafriela_de_frango_from_69579", "chicken"));
//		tasks.add(new RecipeScraper(dataPath, connection, ROOT, "fish_tacos_33979", "taco"));
//		tasks.add(new RecipeScraper(dataPath, connection, ROOT, "spanish_tomato_bread_15677", "bruschetta"));

		// set up threads to consume the task queue
		for (int i = 0; i < THREADS; i++) {
			new Thread(() -> {
				ScraperTask task;
				while ((task = tasks.poll()) != null) {
					task.execute();
				}
			}).run();
		}
	}

	private interface ScraperTask {

		void execute();
	}

	private static class DishIndex implements ScraperTask {

		private static final String INDEX_URL = "/dishes/by/letter/%s";

		private static final Pattern PATTERN = Pattern.compile("([a-z0-9_]+)");

		private final String dataPath;

		private final Connection connection;
		private final String rootUrl;

		private final Queue<ScraperTask> tasks;

		private final String url;

		private DishIndex(String dataPath, Connection connection, String rootUrl, char letter, Queue<ScraperTask> tasks) {
			this.dataPath = dataPath;
			this.connection = connection;
			this.rootUrl = rootUrl;

			this.tasks = tasks;

			this.url = rootUrl + String.format(INDEX_URL, letter);
		}

		@Override
		public void execute() {
			try {
				Document doc = connection.url(url).get();
				Elements dishes = doc.select("#foods-by-letter ol.grid-view li");

				dishes.parallelStream()
					  .map(Element::id)
					  .map(PATTERN::matcher)
					  .filter(Matcher::matches)
					  .map(Matcher::group)
					  .forEach(d -> tasks.add(new RecipeList(dataPath, connection, rootUrl, d, 1, tasks)));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static class RecipeList implements ScraperTask {

		private static final String LIST_URL = "/recipes/search?dishes[]=%s";

		private static final Pattern COUNT_PATTERN = Pattern.compile("(\\d+) results.+");
		private static final Pattern LINK_PATTERN = Pattern.compile("/food/recipes/([a-z0-9_]+)");

		private final String dataPath;

		private final Connection connection;
		private final String rootUrl;
		private final String type;
		private final int page;

		private final Queue<ScraperTask> tasks;

		private final String url;

		private RecipeList(String dataPath, Connection connection, String rootUrl, String type, int page, Queue<ScraperTask> tasks) {
			this.dataPath = dataPath;
			this.connection = connection;
			this.rootUrl = rootUrl;
			this.type = type;
			this.page = page;

			this.tasks = tasks;

			this.url = rootUrl + String.format(LIST_URL, type);
		}

		@Override
		public void execute() {
			try {
				Document doc = connection.url(url + "&page=" + page).get();

				if (page == 1) {
					// add another recipe list task for each page
					Elements pages = doc.select("#queryBox p");
					pages.stream()
						 .map(Element::text)
						 .map(COUNT_PATTERN::matcher)
						 .filter(Matcher::find)
						 .map(m -> m.group(1))
						 .map(Integer::valueOf)
						 .filter(c -> c > 15)
						 .findFirst()
						 .ifPresent(c -> IntStream.range(2, (int)Math.ceil(c / 15f) + 1)
												  .forEach(p -> tasks.add(new RecipeList(dataPath, connection, rootUrl, type, p, tasks))));
				}

				Elements recipes = doc.select("#article-list .article h3 a");

				recipes.parallelStream()
					   .map(e -> e.attr("href"))
					   .map(LINK_PATTERN::matcher)
					   .filter(Matcher::matches)
					   .map(m -> m.group(1))
					   .peek(System.out::println)
					   .forEach(r -> new RecipeScraper(dataPath, connection, rootUrl, r, type));
			} catch (HttpStatusException e) {
				if (e.getStatusCode() == 503) {
					System.out.println("*** Retry failed search for type " + type + " pg " + page);
					tasks.add(this);
				} else {
					e.printStackTrace();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private static class RecipeScraper implements ScraperTask {

		private static final String RECIPE_URL = "/recipes/%s";

		private final String dataPath;

		private final Connection connection;
		private final String id;
		private final String type;

		private final String url;
		private final String dir;

		private RecipeScraper(String dataPath, Connection connection, String rootUrl, String id, String type) {
			this.dataPath = dataPath;
			this.connection = connection;
			this.id = id;
			this.type = type;
			this.url = rootUrl + String.format(RECIPE_URL, id);

			Path path = Paths.get(dataPath, type);

			try {
				if (!Files.exists(path)) Files.createDirectories(path);
			} catch (IOException e) {
				e.printStackTrace();
			}

			this.dir = path.toString() + File.separator;
		}

		@Override
		public void execute() {
			try {
				Document doc = connection.url(url).get();
				Element el;

				Recipe recipe = new Recipe();
				recipe.id = id;
				recipe.type = type;
				recipe.title = doc.select(".recipe-main-info .recipe-title--small-spacing h1").first().text();
				recipe.description = doc.select(".recipe-main-info .recipe-description").first().text();

				el = doc.select(".recipe-media img.recipe-media__image").first();
				if (el == null) el = doc.select(".recipe-media .emp-placeholder img").first();
				recipe.image = el == null ? null : downloadImage(el.absUrl("src"), dir);

				el = doc.select(".recipe-media img.recipe-media__image").first();
				recipe.image = el == null ? null : downloadImage(el.absUrl("src"), dir);

				recipe.meta = new Recipe.RecipeMeta();

				el = doc.select(".recipe-main-info .recipe-metadata__prep-time").first();
				recipe.meta.prepTime = el == null ? null : el.text();

				el = doc.select(".recipe-main-info .recipe-metadata__cook-time").first();
				recipe.meta.cookTime = el == null ? null : el.text();

				el = doc.select(".recipe-main-info .recipe-metadata__serving").first();
				recipe.meta.servings = el == null ? null : el.text();

				recipe.meta.diets = doc.select(".recipe-metadata__dietary a p").stream()
									   .map(Element::text)
									   .collect(Collectors.toSet());

				recipe.source = new Recipe.RecipeSource();

				el = doc.select(".recipe-chef .chef__name a.chef__link").first();
				recipe.source.author = el == null ? null : el.text();

				el = doc.select(".recipe-chef .chef__programme-name a.chef__link").first();
				recipe.source.from = el == null ? null : el.text();

				recipe.method = new Recipe.RecipeMethod();
				recipe.method.steps = doc.select(".recipe-method li.recipe-method__list-item").stream()
										 .map(Element::text)
										 .collect(Collectors.toList());
				el = doc.select("#recipe-tips .recipe-tips__text").first();
				recipe.method.tips = el == null ? null : el.text();

				Elements ingredientGroups = doc.select(".recipe-ingredients-wrapper .recipe-ingredients__sub-heading");
				if (!ingredientGroups.isEmpty()) {
					recipe.ingredients = ingredientGroups.stream()
														 .map(e -> {
															 Recipe.IngredientsList l = new Recipe.IngredientsList();
															 l.title = e.text();
															 l.ingredients = e.nextElementSibling().getElementsByTag("li").stream()
																			  .map(Element::text)
																			  .collect(Collectors.toList());
															 return l;
														 }).collect(Collectors.toList());
				} else {
					// default ingredients
					Recipe.IngredientsList ingredients = new Recipe.IngredientsList();
					ingredients.title = "Default";
					ingredients.ingredients = doc.select(".recipe-ingredients-wrapper li.recipe-ingredients__list-item").stream()
												 .map(Element::text)
												 .collect(Collectors.toList());
					recipe.ingredients = Collections.singletonList(ingredients);
				}

				JSONMAPPER.writeValue(Files.newOutputStream(Paths.get(this.dir, id + ".json")), recipe);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private String downloadImage(String url, String outPath) throws IOException {
			String name = url.substring(url.lastIndexOf("/") + 1);

			//Open a URL Stream
			Connection.Response resultImageResponse = Jsoup.connect(url).ignoreContentType(true).execute();

			// output here
			FileOutputStream out = (new FileOutputStream(new java.io.File(outPath + name)));
			out.write(resultImageResponse.bodyAsBytes());
			out.close();

			return name;
		}
	}

	public static class Recipe implements Serializable {

		private static final long serialVersionUID = 1L;

		public String type;
		public String id;
		public String title;
		public String description;
		public String image;

		public RecipeSource source;

		public RecipeMeta meta;

		public List<IngredientsList> ingredients;

		public RecipeMethod method;

		public static class RecipeSource implements Serializable {

			private static final long serialVersionUID = 1L;

			public String author;
			public String from;
		}

		public static class RecipeMeta implements Serializable {

			private static final long serialVersionUID = 1L;

			public String prepTime;
			public String cookTime;
			public String servings;

			public Set<String> diets;
		}

		public static class IngredientsList implements Serializable {

			private static final long serialVersionUID = 1L;

			public String title;
			public List<String> ingredients;
		}

		public static class RecipeMethod implements Serializable {

			private static final long serialVersionUID = 1L;

			public List<String> steps;
			public String tips;
		}

	}
}
