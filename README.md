# Gamboni's Technical Libraries

Some shared utilities and frameworks for applications in the `org.gamboni` group.

## Disclaimer

☢️ *DON'T EVEN THINK OF USING THIS FOR ANYTHING IMPORTANT!* ☢️

This is a playground for experimental designs, at times hilariously inappropriate. There is no implied stability or usability.

## Design Principles

### What belongs here

The guiding principle is that any technical bug or inconsistency may only be caused by a coding error in a technical library or technical package. (A "technical" bug is opposed to business or specification bugs where the program does what is plainly stated in the code, which might make sense in another context but isn't actually what we want.) Therefore, any time a technical bug is caused by a coding error in business code, it means the business code is too technical, and therefore the technical framework in use is insufficiently powerful to abstract such technical details.

As a corrolary, once the technical framework in use is sufficiently powerful, *all* technical bugs may be solved by fixing framework code, without making a single change in the application code.

I usually start writing technical code (which may not contain or refer to any business concept) in the application itself, in well-identified `tech` packages, and once I decide it is somewhat mature enough, I move it to this `org.gamboni.tech` repository.

### Removing the need to refresh

I want my applications to constantly show live data (except when the browser is offline, in which case the application should remain useable and will refresh itself automatically once connectivity is available again). In other words, the "refresh" function should never be required. Resolving conflicts that occur between remote and local changes during such a connectivity interruption is a *business* concern and must therefore be handled outside of this repository, although tools to do so easily may be provided. As an example, for my shopping-list application, an item being in the `TO_BUY` state being switched to both `NOT_IN_LIST` and `BOUGHT` by two concurrent users will go to `BOUGHT`: even if the item was not actually needed, it's been bought anyway. This reasoning belongs in the application's business rules/code.

### Avoid initial roundtrip to populate the screen

All applications using these technical libraries perform server-side rendering of the page, to avoid the unpleasant experience of loading a blank application which needs to call the server again before it can display anything. This has the nice side effect that the server-rendered page can be cached (e.g. when the browser is closed and re-opened) and displayed again, even if the browser is offline. It will automatically "catch up" once connectivity is available again, as described before.

Of course, it should never be required to implement both back-end and front-end rendering of an element because that would create the possibility that the two behave differently, which qualifies as a technical bug (more generally, any time the application look changes meaningfully upon refresh, it is a technical bug). A common strategy for such a feature is to first implement it in Javascript (or a language that compiles to that), and then interpret the Javascript in the back end when server-side rendering is required. Another approach is to always generate HTML fragments in the back end and replace all or part of the page as required. However that requires re-sending and re-rendering potentially large chunks of the page even when a small part (such as a CSS class) changed.

As I am more familiar with Java I decided to take a third approach and implement the entire applications and technical frameworks in Java. The framework takes care of generating Javascript logic to update the server-rendered code when needed.

### Data Store, Stamps and Events

This technical framework favours using a *store* as an abstraction to the overall (back-end) application state. The store is typically database-backed, but at least one application I wrote (my "`mserver`" web front end to mplayer) keeps all data in memory.

As all my applications have a very small number of users (mostly just my family) I can afford having a global counter (the`stamp`) for all changes occurring in an application. Any server-rendered UI then also carries the corresponding stamp value. This allows a client receiving said UI to subscribe to relevant data changes that have occurred and will occur after that value, which is important when re-opening a cached application state.


