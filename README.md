# Simple Rate Limiter

A simple rate limiter to limit requests made to API endpoints or any other resource. 


## Installation

Use the package manager [pip](https://pip.pypa.io/en/stable/) to install foobar.

```sbt
pip install foobar
```

## Usage

```scala
  import scala.concurrent.duration.FiniteDuration
  import java.util.concurrent.TimeUnit
  import hu.netkatalogus.ratelimit.RateLimiter
  import hu.netkatalogus.ratelimit.RateLimiter.{
    Blocked, LimiterConfig, Result, Success
  }

  // how many requests can be made in the given time period
  val numberOfRequests = 3
  // the requests should be made in 500 milliseconds
  val timeInterval = new FiniteDuration(500, TimeUnit.MILLISECONDS)
  // we also specify the name of the endpoint for logging
  // and a function to derive a key from the request identity
  // here it's just String => String but it can be Any => String
  val config = LimiterConfig("test-endpoint", numberOfRequests, timeInterval, (s: String) => s)
  
  val limiter: RateLimiter[String] = new RateLimiter[String](config, this)

  limiter.apply("request-user-id", () => {
    // perform request
  }) == Success(_)

    
```

## Contributing
Pull requests are welcome. 

Please make sure to update tests as appropriate.

## License
[MIT](https://choosealicense.com/licenses/mit/)