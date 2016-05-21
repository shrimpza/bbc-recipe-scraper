package net.shrimpworks.bbc.recipes.chefs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Queue;
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

public class ChefSearch implements ScraperTask {

	private static final String LIST_URL = "/recipes/search?chefs[]=%s";

	private static final Pattern COUNT_PATTERN = Pattern.compile("(\\d+) results.+");
	private static final Pattern LINK_PATTERN = Pattern.compile("/food/recipes/([a-z0-9_\\-]+)");

	private final String dataPath;

	private final String rootUrl;
	private final String chef;
	private final int page;

	private final Queue<ScraperTask> taskQueue;

	private final String url;

	public ChefSearch(String dataPath, String rootUrl, String chef, int page, Queue<ScraperTask> taskQueue) {
		this.dataPath = dataPath;
		this.rootUrl = rootUrl;
		this.chef = chef;
		this.page = page;

		this.taskQueue = taskQueue;

		this.url = rootUrl + String.format(LIST_URL, chef);
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
											  .forEach(p -> taskQueue.add(new ChefSearch(dataPath, rootUrl, chef, p, taskQueue))));
			}

			Elements recipes = doc.select("#article-list .article h3 a");

			recipes.parallelStream()
				   .map(e -> e.attr("href"))
				   .map(LINK_PATTERN::matcher)
				   .filter(Matcher::matches)
				   .map(m -> m.group(1))
				   .filter(r -> !Files.exists(Paths.get(dataPath, r, r + ".json"))) // skip existing files
				   .forEach(r -> taskQueue.add(new RecipeScraper(dataPath, rootUrl, r, taskQueue)));
		} catch (HttpStatusException e) {
			// note - the search URLs often return error 503, so we re-submit this task to be tried again shortly
			if (e.getStatusCode() == 503) {
				System.out.print("x");
				taskQueue.add(this);
			} else {
				e.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
