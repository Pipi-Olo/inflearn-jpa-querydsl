> 이 글은 김영한님의 **'스프링 부트와 JPA 실무 완전 정복 로드맵'** 강의를 듣고 정리한 내용입니다.
> 강의 : [실전! Querydsl](https://www.inflearn.com/course/Querydsl-%EC%8B%A4%EC%A0%84/)

---
# 도메인 분석
```java
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Member {

    @Id @GeneratedValue
    @Column(name = "member_id")
    private Long id;

    private String username;
    private int age;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;
}
```

```java
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Team {

    @Id @GeneratedValue
    @Column(name = "team_id")
    private Long id;

    private String name;

    @OneToMany(mappedBy = "team")
    private List<Member> members = new ArrayList<>();
}
```

```java
@Data
public class MemberTeamDto {

    private Long memberId;
    private String username;
    private int age;
    private Long teamId;
    private String teamName;

    @QueryProjection
    public MemberTeamDto(Long memberId, String username, int age, 
    					 Long teamId, String teamName) {
        this.memberId = memberId;
        this.username = username;
        this.age = age;
        this.teamId = teamId;
        this.teamName = teamName;
    }
}
```

* `@NoArgsConstructor(access = AccessLevel.PROTECTED)`
  * 엔티티는 Public 혹은 Protected 기본 생성자를 필수로 가져야 한다.
* `@JoinColumn(name = "team_id")`
  * Member 테이블의 외래키 컬럼 명을 `team_id` 로 설정한다.
  * 생략할 경우, `{Team 테이블}_{Team 테이블 PK}` 으로 설정된다. → `team_team_id`
  * Team 테이블의 PK 와 맞춘다.
* `@QueryProjection`
  * 해당 DTO 를 Q 클래스로 생성한다.
  * DTO 직접 조회일 때, 생성자를 통해서 편리하게 DTO 타입 데이터를 얻을 수 있다.
  * DTO 가 특정 기술에 의존한다는 단점이 있다.
    * DTO 는 클라이언트, 컨트롤러, 서비스 등 다양한 계층에서 사용된다.

---
# 기본 문법
## JPA vs Querydsl
```java
@Test
void startJPQL() {
	String jpql = "select m from Member m where m.username = :username";
    Member findMember = em.createQuery(jpql, Member.class)
    			.setParameter("username", "member1")
                .getSingleResult();

}
  
@Test
void startQuerydsl() {
	JPAQueryFactory query = new JPAQueryFactory(em); 
    
	QMember m = new QMember("m");
    Member findMember = query
				.select(m)
				.from(m)
				.where(m.username.eq("member1")) // 파라미터 바인딩 처리
                .fetchOne();
}
```
* JPQL
  * 문자열 (런타임 오류) 
  * 파라미터 바인딩 수동 처리
* Querydsl
  * 자바 코드 (컴파일 오류)
  * 파라미터 바인딩 자동 처리
  * Querydsl 은 JPQL 빌더이다.
* `JPAQueryFactory` 동시성 문제
  * `JPAQueryFactory` 동시성은 EntityManager 에 의존하고 있다.
  * 스프링 프레임워크는 트랜잭션마다 별도의 영속성 컨텍스트를 제공하기 때문에, 여러 쓰레드에서 동시에 EntityManager 에 접근해도 된다.
  
## Q-Type
```java
QMember qMember = new QMember("m"); // 별칭 직접 지정 
QMember qMember = QMember.member;   // 기본 인스턴스 사용
```

* 서브 쿼리, 셀프 조인 등 특별한 경우를 제외하고는 기본 인스턴스를 사용하자.

## 검색 조건 쿼리
```java
@Test
void search() {
	Member findMember = query
    		.selectFrom(member)
            .where(member.username.eq("member1")
            		.and(member.age.eq(10)))
            .fetchOne();
}
```
```java
@Test
void search() {
	List<Member> result = query
	    		.selectFrom(member)
                .where(member.username.eq("member1"), 
                		member.age.eq(10))
                .fetch();
}
```
```java
member.username.eq("member1")       // username = 'member1'
member.username.ne("member1")       // username != 'member1'
member.username.eq("member1").not() // username != 'member1'

member.username.isNotNull() // username is not null
member.age.in(10, 20)       // age in (10,20)
member.age.notIn(10, 20)    // age not in (10, 20)
member.age.between(10,30)   // between 10, 30

member.age.goe(30) // age >= 30
member.age.gt(30)  // age > 30
member.age.loe(30) // age <= 30
member.age.lt(30)  // age < 30

member.username.like("member%")      // like 검색
member.username.contains("member")   // like ‘%member%’ 검색
member.username.startsWith("member") // like ‘member%’ 검색

...
```

* 검색 조건은 `.and()`, `.or()` 를 이용해 메서드 체인으로 연결한다.
* 검색 조건을 `where()` 파라미터로 넘기면 `AND` 조건으로 동작한다.
  * 파라미터가 `null` 이면 무시한다. → 메서드 추출을 사용해 동적 쿼리로 만든다.

## 결과 조회
```java
List<Member> fetch = queryFactory
		.selectFrom(member)
		.fetch();

Member findMember1 = queryFactory
		.selectFrom(member)
        .fetchOne();

Member findMember2 = queryFactory
        .selectFrom(member)
        .fetchFirst();

QueryResults<Member> results = queryFactory
        .selectFrom(member)
        .fetchResults();

long count = queryFactory        
        .selectFrom(member)
        .fetchCount();
```

* `fetch()` → 리스트를 반환한다. 데이터가 없으면 빈 리스트 반환한다.
* `fetchOne()` → 단 건 데이터를 반환한다. 
  * 데이터가 없으면 `null` 을 반환한다.
  * 결과가 둘 이상이면 `NonUniqueResultException` 예외가 발생한다.
* `fetchFirst()` → 첫 번째 데이터를 반환한다. `limit(1).fetchOne()`
* `fetchResults()` → 페이징 정보를 포함한 데이터를 반환한다. 추가적으로 totalCount 쿼리를 호출한다.
  * Querydsl 5.0 부터 Deprecated 되었다. 
    👉 내용과 전체 카운트를 조회하는 쿼리를 별도로 작성해야한다.
* `fetchCount()` : 카운트 쿼리로 변경해서 호출한다.
  * Querydsl 5.0 부터 Deprecated 되었다. 
    👉 `select(Wildcard.count)`, `select(member.count())` 을 사용한다.

## 정렬
```java
@Test
void sort() {
    List<Member> result = query
    		.selectFrom(member)
            .where(member.age.eq(100))
            .orderBy(member.age.desc(), member.username.asc().nullsLast())
            .fetch();
}
```

* `desc()`, `asc()` → 내림차 및 오름차로 정렬한다.
* `nullsLast()`, `nullsFirst()` → `null` 데이터에 대한 순서를 부여한다.

## 페이징
```java
@Test
void paging() {
    QueryResults<Member> result = query
              .selectFrom(member)
              .orderBy(member.username.desc())
              .offset(1)
              .limit(2)
              .fetchResults();	
}
```
```java
@Test
void paging() {
    List<Member> content = query
            .selectFrom(member)
            .orderBy(member.username.desc())
            .offset(1)
            .limit(2)
            .fetch();
            
    long totalCount = query
			.select(member.count())
            .from(member)
            .fetchOne();
}
```

* `fetchResults()` 👉 페이징 정보를 포함해서 반환한다.
  * 자동으로 count 쿼리를 만들어서 호출한다. → 실행되는 쿼리 총 2개
  * 여러 테이블을 조인해서 데이터를 조회할 때, 성능이 좋지 않을 수 있다.
    * 자동으로 만들어주는 count 쿼리는 원본 쿼리와 같이 테이블을 모두 조인하기 때문이다.
  * Querydsl 5.0 부터 `Deprecated` 되었다.
* `fetch()` + `select(member.count())`
  * count 쿼리를 별도로 작성한다.

## 집합
### 집합 함수
```java
@Test
void aggregation() {
	List<Tuple> result = query
    		.select(member.count(),
                    member.age.avg(),
                    member.age.sum(),
                    member.age.max(),
                    member.age.min())
            .from(member)
            .fetch();
            
	Tuple tuple = result.get(0);
    
    Long count  = tuple.get(member.count());
    Double avg  = tuple.get(member.age.avg());
    Integer sum = tuple.get(member.age.sum());
    Integer max = tuple.get(member.age.max());
    Integer min = tuple.get(member.age.min());        
}
```

* JPQL 이 제공하는 모든 집함 함수를 제공한다.
* `Tuple` 타입은 프로젝션 대상이 둘 이상일 때 사용한다.
  * (Integer, Integer) → 어떤 특정 객체 타입으로 받을 수 없다.

### GroupBy
```java
@Test
void group() {
	List<Tuple> result = query
    		.select(team.name, member.age.avg())
            .from(member)
            .join(member.team, team)
            .groupBy(team.name)
            .having(team.size.gt(3))
            .fetch();
}
```

* `groupBy` 를 통해 그룹된 결과에 조건을 추가하려면 `having` 을 사용한다.

## 조인
### 기본 조인
```java
join(조인 대상, 별칭으로 사용할 Q 타입)
```
```java
@Test
void join() {
	QMember member = QMember.member;
	QTeam team = QTeam.team;
    
	List<Member> result = query
    		.selectFrom(member)
            .join(member.team, team)
            .where(team.name.eq("teamA"))
            .fetch();
}
```

* `join()` , `innerJoin()` 👉 내부 조인 (Inner Join)
* `leftJoin()` 👉 Left 외부 조인 (Left Outer Join)
* `rightJoin()` 👉 Rigth 외부 조인 (Rigth Outer Join)

### 연관관계 없는 엔티티 조인
#### 세타 조인
```java
@Test
void join() {
	QMember member = QMember.member;
	QTeam team = QTeam.team;
    
	List<Member> result = query
    		.select(member)
            .from(member, team)
            .where(member.username.eq(team.name))
            .fetch();
}
```

* `from()` 파라미터로 여러 엔티티를 선택하면 세타 조인으로 동작한다.
* 연관관계 없는 엔티티도 조인이 가능하다.
* 외부 조인이 불가능하다. → 조인 ON 을 사용하면 외부 조인이 가능하다.

#### 연관관계 없는 엔티티 외부 조인
```java
@Test
void joinOnNoRelation {
	List<Tuple> result = query
    		.select(member, team)
            .from(member)
            .leftJoin(team)
            	.on(member.username.eq(team.name))
            .fetch();
}
```

* `leftJoin()` 파라미터가 엔티티 1개만 들어간다.
  * 일반 조인일 경우에는 파라미터가 2개 들어간다. → `leftJoin(member.team, team)`
* 연관관계가 없는 엔티티 조인은 ON 절에 있는 조건만 비교한다.
  * 일반 조인은 ON 절이 없어도 기본적으로 `member.team_id = team.team_id` 을 통해 조인한다.
  * 일반 조인은 `id`, `name` 조건으로 비교한다.
  
### 조인 - ON 절
#### 조인 대상 필터링
```java
@Test
void joinOnFiltering() {
	List<Tuple> result = query
            .select(member, team)
            .from(member)
            .leftJoin(member.team, team)
            	.on(team.name.eq("teamA"))
            .fetch();
}
```

* ON 절을 사용해 조인 대상을 필터링 할 때, 내부 조인을 사용하면 where 절에서 필터링 하는 것과 동일하다.
* 외부 조인을 필터링 할 때, ON 절을 사용하자.

### 페치 조인
```java
@Test
void fetchJoin() {
Member findMember = queryFactory
		.selectFrom(member)
        .join(member.team, team)
        	.fetchJoin()
        .where(member.username.eq("member1"))
        .fetchOne();
}
```

* `join()` 등 조인 뒤에 `fetchJoin()` 을 추가하면 된다.

## 서브 쿼리
#### where 절 서브 쿼리
```java
@Test
void subQuery() {
	QMember memberSub = new QMember("memberSub");
    
    List<Member> result = queryFactory
    		.selectFrom(member)
            .where(member.age.eq(
            		JPAExpressions
                    		.select(memberSub.age.max())
                            .from(memberSub))) 
			.fetch();
}
```
#### select 절 서브 쿼리
```java
@Test
void subQuery() {
	List<Tuple> fetch = queryFactory
    		.select(member.username,
            		JPAExpressions
                    	.select(memberSub.age.avg())
                        .from(memberSub))
            .from(member)
            .fetch();
}
```

* `com.querydsl.jpa.JPAExpressions` 를 사용한다.
* JPA 는 from 절 서브쿼리가 불가능하다.
  * JPA 는 where 절 서브 쿼리만 지원한다.
  * 하이버네이트 구현체가 select 절 서브 쿼리를 지원한다.

#### from 절 서브 쿼리 해결방안
* 서브 쿼리를 `join` 으로 변경한다. 불가능할 수도 있다.
* 애플리케이션에서 쿼리를 분리해서 2번 실행한다.
* `nativeSQL` 을 사용한다.

## Case 문
```java
@Test
void case() 
List<String> result = queryFactory
             .select(new CaseBuilder()
             		.when(member.age.between(0, 20)).then("0~20살")
                    .when(member.age.between(21, 30)).then("21~30살")
                    .otherwise("기타"))
             .from(member)
             .fetch();
}
```

### orderBy에서 Case 문 함께 사용하기
```java
@Test
void caseWithOrderBy() {
	NumberExpression<Integer> rankPath = new CaseBuilder()
    		.when(member.age.between(0, 20)).then(2)
            .when(member.age.between(21, 30)).then(1)
            .otherwise(3);
             
	List<Tuple> result = queryFactory
            .select(member.username, member.age, rankPath)
            .from(member)
            .orderBy(rankPath.desc())
            .fetch();
}
```

* Case 문에 해당하는 조건을 `rankPath` 처럼 변수로 선언할 수 있다.
* `select`, `orderBy` 절에 사용할 수 있다.

## 상수, 문자 더하기
#### constant
```java
@Test
void constant() {
	Tuple result = queryFactory
    		.select(member.username, Expressions.constant("A"))
            .from(member)
            .fetchFirst();
]
```

#### concat
```java
@Test
void concat() {
	String result = queryFactory
            .select(member.username
            	.concat("_")
                .concat(member.age.stringValue()))
            .from(member)
            .where(member.username.eq("member1"))
            .fetchOne();
}
```

* `stringValue()` 는 문자가 아닌 타입을 문자로 변환할 수 있다. → `ENUM` 처리에 많이 사용된다.