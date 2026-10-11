# FetchType.LAZY Doesn't Completely Prevent the N+1 Problem in Spring Boot

Have you ever thought `FetchType.LAZY` completely prevents the N+1 problem in Spring Boot?

It doesn't.

## 1. The Hook: The Illusion of Safety

**The Myth:** Every Spring Boot developer is taught to use `FetchType.LAZY` instead of `FetchType.EAGER` to prevent the N+1 query problem.

**The Reality:** You set your mapping to `LAZY`, remove Lombok's `@Data` to avoid logging traps, and test your API. Yet, the JSON response still contains the parent entity details.

**The Ticking Time Bomb:** A silent N+1 query problem can occur right under your nose. With five records, it's harmless. With thousands or millions of records, it can cause excessive database queries, overload your connection pool, and degrade your application's performance.

## 2. The Setup: Our Test Entities

Let's look at the exact entities used to understand where the problem occurs.

### Parent.java

```java
@Entity
@Getter
@Setter
public class Parent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
}
```

### Child.java

```java
@Entity
@Getter
@Setter
public class Child {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id")
    private Parent parent;
}
```

Let's test your intuition. Look at these two database entities. Where does the N+1 problem actually occur?

### Scenario A: You call `parentRepository.findAll()`

**Does it cause N+1 because of this relationship? No.**

The `Parent` entity has no relationship pointing to the `Child` entity. Hibernate can fetch the parent records with a single query, assuming no other associations or application logic trigger additional queries.

### Scenario B: You call `childRepository.findAll()`

**Can it cause N+1? Yes.**

The `Child` entity contains a `@ManyToOne` relationship with `Parent`.

Even though the relationship is configured as `LAZY`, accessing `child.getParent().getName()` for every child can trigger an additional query for each parent that hasn't already been loaded.

This can happen when application code, a logger, or a JSON serializer accesses the relationship.

For example, fetching 100 children and accessing each associated parent individually could result in:

- 1 query to fetch the children.
- Up to 100 additional queries to fetch their parents.

The actual number depends on factors such as the persistence context, repeated parent references, and Hibernate's batch-fetching configuration.

That's the N+1 problem.

## 3. The Core Truth: Why LAZY Alone Fails

The N+1 problem isn't unique to Spring Boot. It can occur in almost any ORM when related entities are loaded individually.

In the Spring Boot ecosystem, however, a combination of Hibernate, Lombok, and Jackson can make this problem particularly easy to miss.

### FetchType.LAZY Is a Deferral, Not a Cure

`FetchType.LAZY` tells Hibernate to defer loading an association until it is accessed, where supported by the mapping and provider.

Instead of immediately loading the associated entity, Hibernate can use a proxy that represents the entity and allows it to be initialized later.

The problem begins when something accesses that proxy and causes Hibernate to execute another database query.

### The Two Hidden Triggers

#### 1. Lombok Trap: `@Data`

Lombok's `@Data` generates methods such as:

- `toString()`
- `equals()`
- `hashCode()`
- Getters and setters

Depending on the generated methods and entity relationships, logging an entity can inadvertently access a lazy association.

For example:

```java
log.info("Child details: {}", child);
```

If the generated `toString()` accesses `parent`, it may initialize the proxy and trigger a database query.

**The safer approach:** Use `@Getter` and `@Setter` on entities and explicitly control which fields participate in `toString()`, `equals()`, and `hashCode()`.

#### 2. Jackson Trap: JSON Serialization

Consider a controller that returns a raw entity:

```java
@GetMapping("/children")
public List<Child> getChildren() {
    return childRepository.findAll();
}
```

When Jackson serializes the response, it accesses the entity's properties through its serialization process.

If the response includes the `parent` relationship, Jackson may access the associated entity's properties and initialize its lazy proxy.

The result?

```text
SELECT * FROM child;

SELECT * FROM parent WHERE id = ?;
SELECT * FROM parent WHERE id = ?;
SELECT * FROM parent WHERE id = ?;
...
```

The additional queries can happen even when your service code never explicitly calls `child.getParent().getName()`.

**Important:** Returning an entity doesn't automatically guarantee N+1. The problem occurs when serialization accesses lazy associations that haven't already been loaded.

## 4. The Pitfall: The `@JsonIgnore` Side Effect

A common attempt to prevent Jackson from accessing the relationship is to add `@JsonIgnore`.

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "parent_id")
@JsonIgnore
private Parent parent;
```

This prevents the relationship from being serialized into the JSON response.

However, it also tells Jackson to ignore the property during deserialization by default. As a result, an incoming payload such as:

```json
{
  "name": "Child A",
  "parent": {
    "id": 1
  }
}
```

may not populate the `parent` field as expected.

This can break POST or PUT requests that rely on this relationship being deserialized.

Let's look at the alternatives.

## 5. The Organized Solutions: How to Fix It

Choose the solution based on whether your API needs to return the parent data.

### Case A: You Don't Want Parent Data in the API Response

Use `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` if you want Jackson to accept the property in incoming JSON but exclude it from outgoing JSON.

```java
@Entity
@Getter
@Setter
public class Child {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parent_id")
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Parent parent;
}
```

**Why it works:**

- **Incoming JSON:** Jackson can deserialize the `parent` property.
- **Outgoing JSON:** Jackson omits the `parent` property.
- **Serialization:** Jackson doesn't traverse this relationship through the omitted property.

This avoids relationship traversal through that field during serialization.

**Important:** This annotation does not automatically validate the parent ID, prevent invalid relationships, or guarantee that the entire application is free from N+1 queries. Those concerns should be handled separately.

### Case B: You Want Parent Data in the API Response

If your response needs parent information, you should fetch the required relationship efficiently instead of relying on lazy loading during serialization.

There are two common approaches.

#### Approach 1: Fetch Joins and `@EntityGraph`

##### Option 1: JPQL `JOIN FETCH`

You can explicitly fetch the associated parent in the same query.

```java
public interface ChildRepository extends JpaRepository<Child, Long> {

    @Query("SELECT c FROM Child c JOIN FETCH c.parent")
    List<Child> findAllWithParent();
}
```

**Advantages:**

- Explicitly declares which relationship should be fetched.
- Usually retrieves children and their parents in a single SQL query.
- Prevents per-child lazy-loading queries for this association.

**Considerations:**

- Collection fetch joins can complicate pagination.
- To-one fetch joins are generally easier to paginate.
- An inner join excludes children without a matching parent; use `LEFT JOIN FETCH` when appropriate.

##### Option 2: Spring Data JPA `@EntityGraph`

If you prefer a declarative approach, use `@EntityGraph`.

```java
public interface ChildRepository extends JpaRepository<Child, Long> {

    @EntityGraph(attributePaths = {"parent"})
    Page<Child> findAll(Pageable pageable);
}
```

**Advantages:**

- Keeps fetch requirements separate from JPQL.
- Works naturally with Spring Data's `Pageable` abstraction for this to-one relationship.
- Allows the persistence provider to fetch the requested association efficiently.

**Important:** An entity graph specifies the relationships that should be fetched, not the exact SQL statement Hibernate must generate. Hibernate may use joins or another fetching strategy. A paginated `Page` may also require a separate count query.

For both approaches, inspect the generated SQL to confirm the actual number of queries.

#### Approach 2: Pagination + Hibernate Batch Fetching

Sometimes, fetching associations through joins isn't the preferred strategy, especially when dealing with complex relationships.

Hibernate batch fetching can reduce the number of queries required to initialize lazy associations.

Add this property to `application.properties`:

```properties
spring.jpa.properties.hibernate.default_batch_fetch_size=20
```

Now, suppose you fetch a page of 20 children and later access their parent associations.

Without batch fetching, Hibernate might execute:

```sql
SELECT * FROM child LIMIT 20;

SELECT * FROM parent WHERE id = ?;
SELECT * FROM parent WHERE id = ?;
-- Additional individual queries
```

With batch fetching enabled, Hibernate can group pending proxy initializations into a query similar to:

```sql
SELECT *
FROM parent
WHERE id IN (1, 2, 3, 4, 5, ...);
```

The exact query and batch size depend on the associations accessed, the number of unique parent IDs, and Hibernate's batching behavior.

**Why it works:**

- Reduces repeated database round trips.
- Groups lazy association loads into batches.
- Can substantially reduce the impact of N+1 queries.

For example, if 20 children reference 20 distinct parents and Hibernate fetches all of them in one batch, the workload could be reduced from 21 queries to 2 queries: one for the children and one for their parents.

If the batch size is 20 and there are 100 distinct parent proxies to initialize, Hibernate may need several batch queries rather than one query per parent.

**Remember:** Batch fetching mitigates N+1; it doesn't necessarily eliminate every additional query. Pagination and batch fetching complement each other, but page size and batch size do not have to be identical.

### Case C: The Production Standard — DTO Projections

My preferred approach for API responses is to avoid exposing raw database entities directly from controllers.

Instead, return DTOs containing only the fields the API actually needs.

For example, define a Java record:

```java
public record ChildResponse(
    Long id,
    String name,
    Long parentId
) {}
```

You can use a repository projection to retrieve the required data directly:

```java
public interface ChildRepository extends JpaRepository<Child, Long> {

    @Query("""
        SELECT new com.example.dto.ChildResponse(
            c.id,
            c.name,
            c.parent.id
        )
        FROM Child c
        """)
    List<ChildResponse> findAllChildResponses();
}
```

Replace `com.example.dto` with the actual package containing your record.

The controller can now return the DTO:

```java
@GetMapping("/children")
public List<ChildResponse> getChildren() {
    return childRepository.findAllChildResponses();
}
```

The response looks like this:

```json
[
  {
    "id": 1,
    "name": "Child A",
    "parentId": 10
  },
  {
    "id": 2,
    "name": "Child B",
    "parentId": 11
  }
]
```

**Why this works:**

- The query retrieves only the data required by the API.
- Jackson serializes a flat DTO instead of traversing an entity graph.
- The response contract is independent of your entity relationships.
- There are no lazy proxies attached to the DTO fields shown above.
- You have more control over the SQL and response structure.

If you need the parent's name as well, include it in the projection:

```java
public record ChildResponse(
    Long id,
    String name,
    Long parentId,
    String parentName
) {}
```

```java
@Query("""
    SELECT new com.example.dto.ChildResponse(
        c.id,
        c.name,
        c.parent.id,
        c.parent.name
    )
    FROM Child c
    """)
List<ChildResponse> findAllChildResponses();
```

This lets Hibernate retrieve the required values without relying on Jackson to initialize lazy associations later.

**Note:** A DTO is not automatically immune to N+1 queries. The benefit comes from designing the query and mapping so that the required data is fetched efficiently. A DTO mapper that traverses lazy relationships one by one can still cause N+1.

## 6. How to Verify the Problem Locally

Don't assume that your application is free from N+1 just because it uses `FetchType.LAZY`.

Enable Hibernate SQL logging in your local environment:

```properties
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.orm.jdbc.bind=TRACE
```

The second property can help you inspect SQL parameter bindings when debugging. Use it carefully, because bind values may contain sensitive information.

Then:

1. Call the endpoint that returns a list of children.
2. Inspect the SQL logs.
3. Check whether Hibernate executes repeated parent queries.
4. Compare the query count before and after applying your fix.
5. Test with realistic data volumes and pagination.

For example, if one endpoint returns 100 children and executes one child query followed by 100 parent queries, you have strong evidence of an N+1 problem.

For more detailed analysis, consider Hibernate statistics or a JDBC query-counting tool in your test environment.

## 7. Final Takeaway

`FetchType.LAZY` is an excellent tool, but it is not an automatic shield against the N+1 problem.

The biggest takeaway is architectural: **stop treating database entities as API response models.**

When you return raw entities from your controller, JSON serialization can access lazy proxies and trigger database queries that aren't obvious from your service code.

Protect your application before it reaches production:

- **Audit your entities:** Prefer explicit Lombok annotations over blindly using `@Data` on relational models.
- **Control serialization:** Use `WRITE_ONLY` when a relationship should be accepted in requests but excluded from responses.
- **Fetch deliberately:** Use `JOIN FETCH` or `@EntityGraph` when your response requires associated entities.
- **Use batch fetching:** Reduce the number of round trips when initializing multiple lazy associations.
- **Adopt DTOs:** Separate API response models from persistence entities and fetch only the required fields.
- **Measure the result:** Inspect generated SQL and test query counts instead of assuming the fix works.

Don't let a simple JSON conversion turn clean backend code into an accidental database overload.

**Try it yourself:** Enable `logging.level.org.hibernate.SQL=DEBUG` in your local configuration, restart your application, and inspect what your queries are actually doing.

What looks like one simple API request might be executing dozens or hundreds of database queries behind the scenes.
