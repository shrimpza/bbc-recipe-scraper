package net.shrimpworks.bbc.recipes.dishes;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import net.shrimpworks.bbc.recipes.ScraperTask;
import net.shrimpworks.bbc.recipes.recipes.RecipeScraper;

public class DishSearch implements ScraperTask {

	private static final String LIST_URL = "/recipes/search?dishes[]=%s";

	private static final Pattern COUNT_PATTERN = Pattern.compile("(\\d+) results.+");
	private static final Pattern LINK_PATTERN = Pattern.compile("/food/recipes/([a-z0-9_]+)");

	private final String dataPath;

	private final String rootUrl;
	private final String type;
	private final int page;

	private final ExecutorService executor;

	private final String url;

	public DishSearch(String dataPath, String rootUrl, String type, int page, ExecutorService executor) {
		this.dataPath = dataPath;
		this.rootUrl = rootUrl;
		this.type = type;
		this.page = page;

		this.executor = executor;

		this.url = rootUrl + String.format(LIST_URL, type);
	}

	@Override
	public void execute(Connection connection) {
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
											  .forEach(p -> executor.submit(() -> new DishSearch(dataPath, rootUrl, type, p,
																								 executor))));
			}

			Elements recipes = doc.select("#article-list .article h3 a");

			recipes.parallelStream()
				   .map(e -> e.attr("href"))
				   .map(LINK_PATTERN::matcher)
				   .filter(Matcher::matches)
				   .map(m -> m.group(1))
				   .forEach(r -> executor.submit(() -> new RecipeScraper(dataPath, rootUrl, r).execute(connection)));
		} catch (HttpStatusException e) {
			if (e.getStatusCode() == 503) {
				System.out.println("*** Retry failed search for type " + type + " pg " + page);
				executor.submit(() -> this.execute(connection));
			} else {
				e.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
