# BBC Food Recipe Scraper

A simple HTML page scraper which finds and stores recipes from the BBC Food
website as JSON files. Images are also downloaded where available.

## Build

Apache Ant is required to build this project.

From the project root directory, execute:

`$ ant`

## Usage

Execute:

`$ java -jar bbc-recipe-scraper.jar <output-path>`

All recipes found and scraped successfully will placed into the `<output-path>`
provided, each in their own directory named after the recipe's unique
identifier. If an image is found for the recipe, it will be placed into the
directory as well.
