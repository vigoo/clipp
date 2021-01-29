// When the user clicks on the search box, we want to toggle the search dropdown
function displayToggleSearch(e) {
  e.preventDefault();
  e.stopPropagation();

  closeDropdownSearch(e);
  
  if (idx === null) {
    console.log("Building search index...");
    prepareIdxAndDocMap();
    console.log("Search index built.");
  }
  const dropdown = document.querySelector("#search-dropdown-content");
  if (dropdown) {
    if (!dropdown.classList.contains("show")) {
      dropdown.classList.add("show");
    }
    document.addEventListener("click", closeDropdownSearch);
    document.addEventListener("keydown", searchOnKeyDown);
    document.addEventListener("keyup", searchOnKeyUp);
  }
}

//We want to prepare the index only after clicking the search bar
var idx = null
const docMap = new Map()

function prepareIdxAndDocMap() {
  const docs = [  
    {
      "title": "Cats Effect",
      "url": "/clipp/docs/catseffect.html",
      "content": "Using with Cats-Effect To use the Cats-Effect interface add the following dependency: libraryDependencies += \"io.github.vigoo\" %% \"clipp-cats-effect\" % \"0.4.0\" Example: import io.github.vigoo.clipp._ import io.github.vigoo.clipp.syntax._ import io.github.vigoo.clipp.parsers._ import io.github.vigoo.clipp.catseffect._ import cats.effect._ object Test extends IOApp { override def run(args: List[String]): IO[ExitCode] = { val paramSpec = for { _ &lt;- metadata(\"zio-test\") x &lt;- flag(\"test parameter\", 'x') } yield x Clipp.parseOrDisplayUsageInfo(args, paramSpec, ExitCode.Error) { x =&gt; IO(println(s\"x was: $x\")).map(_ =&gt; ExitCode.Success) } } }"
    } ,    
    {
      "title": "Getting started",
      "url": "/clipp/docs/",
      "content": "Getting started with clipp Clipp lets you describe an immutable specification of how to turn command line arguments (a sequence of strings) into an application-specific data structure. The specification is monadic, which makes it very easy to express sub-command behavior, as the parsing steps can depend on a previously parsed value. Let’s see a simple example first: import java.io.File import io.github.vigoo.clipp._ import io.github.vigoo.clipp.parsers._ import io.github.vigoo.clipp.syntax._ case class Parameters1(inputUrl: String, outputFile: File, verbose: Boolean) val paramSpec1 = for { _ &lt;- metadata(programName = \"Example 1\") inputUrl &lt;- parameter[String](\"URL to download\", \"url\") outputFile &lt;- parameter[File](\"Target file\", \"file\") verbose &lt;- flag(\"Verbose output\", 'v', \"verbose\") } yield Parameters1(inputUrl, outputFile, verbose) This takes two arguments in order, optionally with a -v or --verbose flag which can be in any location (see the exact semantics below), for example: app -v http://something.to.download /tmp/to By using named parameters, the order of them does not matter anymore: val paramSpec2 = for { _ &lt;- metadata(programName = \"Example 2\") inputUrl &lt;- namedParameter[String](\"URL to download\", \"url\", \"input\") outputFile &lt;- namedParameter[File](\"Target file\", \"file\", \"output\") verbose &lt;- flag(\"Verbose output\", 'v', \"verbose\") } yield Parameters1(inputUrl, outputFile, verbose) These can be specified in any order like: app --output /tmp/to --verbose --input http://something.to.download We can use the optinal modifier to mark parts of the parser optional, making their result of type Option[T]. We can for example modify the previous example to make the output optional (and print the downloaded data to the console if it’s not there): case class Parameters3(inputUrl: String, outputFile: Option[File], verbose: Boolean) val paramSpec3 = for { _ &lt;- metadata(programName = \"Example 3\") inputUrl &lt;- namedParameter[String](\"URL to download\", \"url\", \"input\") outputFile &lt;- optional { namedParameter[File](\"Target file\", \"file\", \"output\") } verbose &lt;- flag(\"Verbose output\", 'v', \"verbose\") } yield Parameters3(inputUrl, outputFile, verbose) Commands Support for commands is a primary feature of clipp. The idea is that at a given point in the sequence of command line arguments, a command selects the mode the application will operate in, and it selects possible parameters accepted after it. It is possible to create a hierarchy of commands. Think of aws-cli as an example. Because the specification is monadic, it is very convenient to express this kind of behavior: sealed trait Subcommand case class Create(name: String) extends Subcommand case class Delete(id: Int) extends Subcommand sealed trait Command case class First(input: String) extends Command case class Second(val1: Int, val2: Option[Int]) extends Command case class Third(interactive: Boolean, subcommand: Subcommand) extends Command case class Parameters4(verbose: Boolean, command: Command) val paramSpec4 = for { _ &lt;- metadata(programName = \"Example 4\") verbose &lt;- flag(\"Verbose output\", 'v', \"verbose\") commandName &lt;- command(\"first\", \"second\", \"third\") command &lt;- commandName match { case \"first\" =&gt; for { input &lt;- namedParameter[String](\"Input value\", \"value\", \"input\") } yield First(input) case \"second\" =&gt; for { val1 &lt;- namedParameter[Int](\"First input value\", \"value\", \"val1\") val2 &lt;- optional { namedParameter[Int](\"Second input value\", \"value\", \"val2\") } } yield Second(val1, val2) case \"third\" =&gt; for { interactive &lt;- flag(\"Interactive mode\", \"interactive\") subcommandName &lt;- command(\"create\", \"delete\") subcommand &lt;- subcommandName match { case \"create\" =&gt; parameter[String](\"Name of the thing to create\", \"name\").map(Create(_)) case \"delete\" =&gt; parameter[Int](\"Id of the thing to delete\", \"id\").map(Delete(_)) } } yield Third(interactive, subcommand) } } yield Parameters4(verbose, command) Semantics The semantics of these parsing commands are the following: flag looks for the given flag before the first command location (if any), in case it finds one it removes it from the list of arguments and returns true. namedParameter[T] looks for a --name value pair of arguments before the first command location (if any), removes both from the list of arguments and parses the value with an instance of the ParameterParser type class parameter[T] takes the first argument that does not start with - before the first command location (if any) and **removes it from the list of arguments, then parses the value with an instance of the ParameterParser type class optional makes parser specification section optional command is a special parameter with a fix set of values, which is parsed by taking the first argument that does not start with - and it drops all the arguments until the command from the list of arguments. The semantics of command strongly influences the set of CLI interfaces parseable by this library, but it is a very important detail for the current implementation."
    } ,    
    {
      "title": "clipp: Home",
      "url": "/clipp/",
      "content": ""
    } ,      
    {
      "title": "Parsers",
      "url": "/clipp/docs/parsers.html",
      "content": "Writing custom parsers It is possible to write custom parameter parsers by implementing the ParameterParser type class, which has the following definition: trait ParameterParser[T] { def parse(value: String): Either[String, T] def default: T } For example the built-in implementation for Int values looks like this: import io.github.vigoo.clipp._ import scala.util._ object parsers { implicit val intParameterParser: ParameterParser[Int] = new ParameterParser[Int] { override def parse(value: String): Either[String, Int] = Try(value.toInt).toEither.left.map(_.getMessage) override def default: Int = 0 } }"
    } ,      
    {
      "title": "Usage info",
      "url": "/clipp/docs/usageinfo.html",
      "content": "Usage info generation The level of freedom the monadic structure gives makes it hard to automatically generate usage info, but the library implements a heuristics that is good in most of the common cases, and also allows some customization. Automatic mode In automatic mode, the library introspects the parameter parser by running it with different automatically generated choices in order to figure out the execution graph. These automatically generated choices are for: flags, trying both true and false commands, trying all the valid commands Let’s see how the usage info generated for the 4th example in the getting started page look like! import io.github.vigoo.clipp.usageinfo._ val usageGraph = UsageInfoExtractor.getUsageDescription(paramSpec4) val usageInfo = UsagePrettyPrinter.prettyPrint(usageGraph) // usageInfo: String = \"\"\"Usage: Example 4 [-v] [command] ... ... ... // // -v, --verbose Verbose output // &lt;command&gt; One of first, second, third // // When command is first: // --input &lt;value&gt; Input value // // When command is second: // --val1 &lt;value&gt; First input value // [--val2 &lt;value&gt;] Second input value (optional) // // When command is third: // --interactive Interactive mode // &lt;command&gt; One of create, delete // // When command is create: // &lt;name&gt; Name of the thing to create // // When command is delete: // &lt;id&gt; Id of the thing to delete // \"\"\" Customizing choices All the syntax functions have variants with withExplicitChoices = List[T] parameters which turns off the automatic branching and uses the given list of values to generate the usage info graph. By providing a single value, the choice can be locked to a fix value. Manual mode In very complex cases the pretty printer part of the library can be still used to display customized information. In this case a custom list of PrettyPrintCommands and an optional ParameterParserMetadata can be provided to the UsagePrettyPrinter. Partially locked choices In case of showing the usage info by reacting to bad user input, it is possible to use the state of the parser up until the error to lock the choices to specific values. This has the same effect as locking them to a particular value statically with the withExplicitChoices = List(x) syntax. This can be used to display only relevant parts of the usage info, for example in sub-command style cases."
    } ,    
    {
      "title": "ZIO",
      "url": "/clipp/docs/zio.html",
      "content": "Using with ZIO To use the ZIO interface add the following dependency: libraryDependencies += \"io.github.vigoo\" %% \"clipp-zio\" % \"0.4.0\" It is possible to directly call the ZIO interface wrapper, for example: import io.github.vigoo.clipp._ import io.github.vigoo.clipp.parsers._ import io.github.vigoo.clipp.syntax._ import io.github.vigoo.clipp.zioapi._ import zio._ object Test1 extends zio.App { override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = { val paramSpec = for { _ &lt;- metadata(\"zio-test\") x &lt;- flag(\"test parameter\", 'x') } yield x Clipp.parseOrDisplayUsageInfo(args, paramSpec, ExitCode.failure) { x =&gt; console.putStrLn(s\"x was: $x\").as(ExitCode.success) }.catchAll { _: ParserFailure =&gt; ZIO.succeed(ExitCode.failure) } } } An even better alternative is to construct a ZLayer from the parameters: import io.github.vigoo.clipp._ import io.github.vigoo.clipp.parsers._ import io.github.vigoo.clipp.syntax._ import io.github.vigoo.clipp.zioapi._ import io.github.vigoo.clipp.zioapi.config import zio._ object Test2 extends zio.App { override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = { val paramSpec = for { _ &lt;- metadata(\"zio-test\") x &lt;- flag(\"test parameter\", 'x') } yield x val clippConfig = config.fromArgsWithUsageInfo(args, paramSpec) val program = for { x &lt;- config.parameters[Boolean] _ &lt;- console.putStrLn(s\"x was: $x\") } yield ExitCode.success program .provideSomeLayer(clippConfig) .catchAll { _: ParserFailure =&gt; ZIO.succeed(ExitCode.failure) } } }"
    } ,        
  ];

  idx = lunr(function () {
    this.ref("title");
    this.field("content");

    docs.forEach(function (doc) {
      this.add(doc);
    }, this);
  });

  docs.forEach(function (doc) {
    docMap.set(doc.title, doc.url);
  });
}

// The onkeypress handler for search functionality
function searchOnKeyDown(e) {
  const keyCode = e.keyCode;
  const parent = e.target.parentElement;
  const isSearchBar = e.target.id === "search-bar";
  const isSearchResult = parent ? parent.id.startsWith("result-") : false;
  const isSearchBarOrResult = isSearchBar || isSearchResult;

  if (keyCode === 40 && isSearchBarOrResult) {
    // On 'down', try to navigate down the search results
    e.preventDefault();
    e.stopPropagation();
    selectDown(e);
  } else if (keyCode === 38 && isSearchBarOrResult) {
    // On 'up', try to navigate up the search results
    e.preventDefault();
    e.stopPropagation();
    selectUp(e);
  } else if (keyCode === 27 && isSearchBarOrResult) {
    // On 'ESC', close the search dropdown
    e.preventDefault();
    e.stopPropagation();
    closeDropdownSearch(e);
  }
}

// Search is only done on key-up so that the search terms are properly propagated
function searchOnKeyUp(e) {
  // Filter out up, down, esc keys
  const keyCode = e.keyCode;
  const cannotBe = [40, 38, 27];
  const isSearchBar = e.target.id === "search-bar";
  const keyIsNotWrong = !cannotBe.includes(keyCode);
  if (isSearchBar && keyIsNotWrong) {
    // Try to run a search
    runSearch(e);
  }
}

// Move the cursor up the search list
function selectUp(e) {
  if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index) && (index > 0)) {
      const nextIndexStr = "result-" + (index - 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Move the cursor down the search list
function selectDown(e) {
  if (e.target.id === "search-bar") {
    const firstResult = document.querySelector("li[id$='result-0']");
    if (firstResult) {
      firstResult.firstChild.focus();
    }
  } else if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index)) {
      const nextIndexStr = "result-" + (index + 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Search for whatever the user has typed so far
function runSearch(e) {
  if (e.target.value === "") {
    // On empty string, remove all search results
    // Otherwise this may show all results as everything is a "match"
    applySearchResults([]);
  } else {
    const tokens = e.target.value.split(" ");
    const moddedTokens = tokens.map(function (token) {
      // "*" + token + "*"
      return token;
    })
    const searchTerm = moddedTokens.join(" ");
    const searchResults = idx.search(searchTerm);
    const mapResults = searchResults.map(function (result) {
      const resultUrl = docMap.get(result.ref);
      return { name: result.ref, url: resultUrl };
    })

    applySearchResults(mapResults);
  }

}

// After a search, modify the search dropdown to contain the search results
function applySearchResults(results) {
  const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
  if (dropdown) {
    //Remove each child
    while (dropdown.firstChild) {
      dropdown.removeChild(dropdown.firstChild);
    }

    //Add each result as an element in the list
    results.forEach(function (result, i) {
      const elem = document.createElement("li");
      elem.setAttribute("class", "dropdown-item");
      elem.setAttribute("id", "result-" + i);

      const elemLink = document.createElement("a");
      elemLink.setAttribute("title", result.name);
      elemLink.setAttribute("href", result.url);
      elemLink.setAttribute("class", "dropdown-item-link");

      const elemLinkText = document.createElement("span");
      elemLinkText.setAttribute("class", "dropdown-item-link-text");
      elemLinkText.innerHTML = result.name;

      elemLink.appendChild(elemLinkText);
      elem.appendChild(elemLink);
      dropdown.appendChild(elem);
    });
  }
}

// Close the dropdown if the user clicks (only) outside of it
function closeDropdownSearch(e) {
  // Check if where we're clicking is the search dropdown
  if (e.target.id !== "search-bar") {
    const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
    if (dropdown) {
      dropdown.classList.remove("show");
      document.documentElement.removeEventListener("click", closeDropdownSearch);
    }
  }
}
