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
* 별칭이 같으면 구별할 수가 없다.

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

## 중복 제거
```java
List<String> result = queryFactory
			.select(member.username).distinct()
            .from(member)
            .fetch();
```

* `distinct()` 는 JPQL distinct 와 동일하게 동작한다.
* SQL distinct + 애플리케이션에서 엔티티 중복 제거를 실행한다.

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

#### from 절 서브쿼리 해결방안
* 서브 쿼리를 `join` 으로 변경한다. 불가능할 수도 있다.
* 애플리케이션에서 쿼리를 분리해서 2번 실행한다.
* `nativeSQL` 을 사용한다.

> from 절 서브 쿼리 사용 이유
> 특정 화면에 종속적인 데이터를 위해 from 절 서브 쿼리를 사용하는 경우가 종종 있다. 
> 재사용성이 너무 떨어지기 때문에 좋지 않다. 
> 특정 화면에 맞추기 위해 데이터를 조작하는 행위는 애플리케이션 혹은 뷰에서 해결할 문제이다. 데이터베이스에서 해결할 문제는 아니다.

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

> Case 문을 언제 써야할 까?
> 조회하는 데이터 수를 줄이는 where, filtering 기능은 좋다. 하지만, 데이터를 가공하는 행위는 데이터베이스에서 하지 않는 것이 좋다. 효율, 성능 등 특별한 경우를 제외하고는 다른 방법을 사용하자.

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

---

# 중급 문법
## 프로젝션 결과 반환 - 기본
### 프로젝션 대상이 1개일 때
```java
List<String> result = queryFactory
            .select(member.username)
            .from(member)
            .fetch();
```

* select 절에 지정된 대상을 프로젝션이라 한다.
* 프로젝션 대상이 하나면 타입을 명확하게 지정할 수 있다.

### 프로젝션 대상이 둘 이상일 때
* 프로젝션 대상이 둘 이상이면 튜플이나 DTO 로 조회한다.

#### 튜플 조회
```java
List<Tuple> result = queryFactory
		.select(member.username, member.age)
        .from(member)
        .fetch();
        
for (Tuple tuple : result) {
    String username = tuple.get(member.username);
	Integer age = tuple.get(member.age);
}
```

* `Tuple` 은 Querydsl 기술이다. → 리포지토리 이외 계층에서 사용하는 것은 안 좋다.
  * 컨트롤러, 서비스 계층이 특정 데이터베이스 기술에 종속적이게 된다.
* 리포지토리에서 반환할 때는 `Tuple` 이 아닌, DTO 형태로 반환해야 한다.

#### 순수 JPA DTO 조회
```java
@Data
public class MemberDto {
	private String username;
    private int age;
}
```
```java
List<MemberDto> result = em.createQuery(
			"select new study.querydsl.dto.MemberDto(m.username, m.age) " +
        		"from Member m", MemberDto.class)
        	.getResultList();
```

* 순수 JPA DTO 를 조회할 때는 new 명령어와 패키지명이 필요하다.
* 생성자 방식만 지원한다.

### Projections.xxx() DTO 조회
* 패키지 명이 필요없다.

#### 프로퍼티 접근 - Projections.bean
```java
List<MemberDto> result = queryFactory
        .select(Projections.bean(MemberDto.class,
        			member.username,
                    member.age))
        .from(member)
		.fetch();
```

* 기본 생성자가 필수이다.
* 먼저 `MemberDto` 객체를 생성하고 setter 메소드를 통해 값을 저장한다.

#### 필드 직접 접근 - Projections.fields
```java
List<MemberDto> result = queryFactory
        .select(Projections.fields(MemberDto.class,
        			member.username,
                    member.age))
        .from(member)
		.fetch(); 
```

* setter 메소드가 없어도 private 필드에 값을 저장할 수 있다.

#### 별칭이 다를 때 - as
```java
@Data
public class UserDto {
	private String name; // != username
    private int age;
}
```
```java
List<UserDto> fetch = queryFactory
			.select(Projections.fields(UserDto.class,
					member.username.as("name"),
                	ExpressionUtils.as(JPAExpressions
        					.select(memberSub.age.max())
        					.from(memberSub), "age")))
			.from(member)
			.fetch();
```

* Member 엔티티의 `username` 과 UserDto 의 `name` 변수명이 다르다.
* 프로퍼티나 필드 접근 생성 방식에서 이름이 다를 때, `as` 를 사용한다.
  * `member.username.as("name")` 👉 필드에 별칭을 적용한다.
  * `ExpressionUtils.as(source, alias)` 👉 필드 혹은 서브 쿼리에 별칭을 적용한다.

#### 생성자 사용 - Projections.constructor
```java
List<MemberDto> result = queryFactory
		.select(Projections.constructor(MemberDto.class,
        		member.username,
       			member.age))
		.from(member)
		.fetch();
```

* 이 방법은 기본 생성자가 없어도 된다.

## 프로젝션 결과 반환 - @QueryProjection
#### 생성자 + @QueryProjection
```java
@Data
public class MemberDto {
	private String username;
    private int age;
      
    @QueryProjection
    public MemberDto(String username, int age) {
        this.username = username;
        this.age = age;
    }
}
```
```java
List<MemberDto> result = queryFactory
			.select(new QMemberDto(member.username, member.age))
            .from(member)
            .fetch();
```

* `@QueryProjection` 애노테이션으로 인해 `QMemberDto` 가 생성된다.
* `Projections.xxx()` 방식과 다르게 컴파일러 시점에 타입 체크를 할 수 있다.
* 가장 안정적인 방법이나, DTO 가 특정 데이터베이스 기술에 종속된다는 단점이 있다.

## 동적 쿼리
### BooleanBuilder
```java
@Test
void dynamicQuery_booleanBuilder() {
	String usernameParam = "member1";
    Integer ageParam = 10;
        
    List<Member> result = search(usernameParam, ageParam);
}

private List<Member> search(String usernameCond, Integer ageCond) {
	BooleanBuilder builder = new BooleanBuilder();
    if (usernameCond != null) {
        builder.and(member.username.eq(usernameCond));
    }
      
    if (ageCond != null) {
        builder.and(member.age.eq(ageCond));
    }
    
    return query
    		.selectFrom(member)
            .where(builder)
            .fetch();
}

```

### Where 다중 파라미터
```java
@Test
void dynamicQuery_whereParam() { 
	String usernameParam = "member1";
	Integer ageParam = 10;
    
    List<Member> result = search(usernameParam, ageParam);
}

private List<Member> search(String usernameCond, Integer ageCond) {
	return query
    		.selectFrom(member)
            .where(usernameEq(usernameCond), ageEq(ageCond))
            .fetch();
}

private BooleanExpression usernameEq(String usernameCond) {
	if (usernameCond == null) {
    	return null;
    }
    
	return member.username.eq(usernameCond);
}
  
private BooleanExpression ageEq(Integer ageCond) {
	return ageCond != null ? member.age.eq(ageCond) : null;
}
```

* `where()` 조건에 `null` 값은 자동으로 무시된다. → 동적 쿼리가 가능해진다.
* 메서드를 통해 다른 쿼리에서도 재사용할 수 있다.
  * 메서드들을 조합해서 새로운 조건을 만들 수 있다.
* 쿼리의 가독성이 높아진다.
* Where 다중 파라미터를 사용하자.

#### 조합
```java
private BooleanExpression allEq(String usernameCond, Integer ageCond) {
    return usernameEq(usernameCond).and(ageEq(ageCond));
}
```

* 조합 기능을 사용하기 위해서는 반환 값이 `BooleanExpression` 이어야 한다.
* 기존에는 `Predicate` 반환 값이다.
  * `Predicate` → 인터페이스
  * `BooleanExpression` → 구현체

## 수정, 삭제 벌크 연산
#### 쿼리 한번으로 대량 데이터 수정
```java
long count = queryFactory
		.update(member)
        .set(member.age, member.age.add(1))
        //.set(member.age, member.age.add(-1))
        //.set(member.age, member.age.multiply(2))
        .execute();
```

* 빼기가 필요한 경우, 음수 값을 더해야 한다.
* 반환 값은 벌크 연산이 적용된 데이터 수이다.

#### 쿼리 한번으로 대량 데이터 삭제
```java
long count = queryFactory
		.delete(member)
        .where(member.age.gt(18))
        .execute();
```

* JPA 와 동일하게 영속성 켄텍스트를 무시하고 실행된다. 👉 영속성 컨텍스트 != 데이터베이스 상황이 발생한다.
* 벌크 연산을 실행하고 항상 영속성 컨텍스트를 초기화한다.

---

# 스프링 데이터 JPA + Querydsl
## 사용자 정의 리포지토리
![](https://velog.velcdn.com/images/pipiolo/post/f4a23be1-3a11-46f4-a5f6-bf8e8dc49763/image.png)

* 스프링 데이터 JPA 는 인터페이스만 상속하면 구현체를 만들어 준다. → Querydsl 코드가 작성 되어야 할 구현체가 필요하다.
* `MemberRepositoryCustom` 인터페이스를 작성한다.
  → `MemberRepository` 가 `MemberRepositoryCustom` 를 상속한다.
  → `MemberRepositoryImpl` 혹은 `MemberRepositoryCustomImpl` 이름으로 구현체를 생성한다.
  → 스프링 데이터 JPA 가 구현체를 만들 때, `MemberRepositoryImpl` 구현체 기능을 포함한다.
  
## 스프링 데이터 페이징 + Querydsl 페이징
```java
public interface MemberRepository extends
        JpaRepository<Member, Long>,
        MemberRepositoryCustom
{        
	List<Member> findByUsername(String username);
}
```
```java
public interface MemberRepositoryCustom {

	List<MemberTeamDto> search(MemberSearchCondition condition);
}
```
```java
public class MemberRepositoryCustomImpl implements MemberRepositoryCustom {

    private final JPAQueryFactory query;

    public MemberRepositoryCustomImpl(EntityManager em) {
        this.query = new JPAQueryFactory(em);
    }
    
    @Override
    public Page<MemberTeamDto> search(MemberSearchCond condition, Pageable pageable) {
        List<MemberTeamDto> content = query
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")))
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = query
                .select(member.count())
                .from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe())
                );
		
        // return new PageImpl<>(content, pageable, total);
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
    
    private BooleanExpression usernameEq(String username) {
        return hasText(username) ? member.username.eq(username) : null;
    }

    private BooleanExpression teamNameEq(String teamName) {
        return hasText(teamName) ? team.name.eq(teamName) : null;
    }

    private BooleanExpression ageGoe(Integer ageGoe) {
        return ageGoe != null ? member.age.goe(ageGoe) : null;
    }

    private BooleanExpression ageLoe(Integer ageLoe) {
        return ageLoe != null ? member.age.loe(ageLoe) : null;
    }
}
```
* `fetchResult()`, `fetchCount()` 👉 `Deprecated`
  * 개발자가 작성한 select 쿼리를 기반으로 내부에서 count 쿼리를 생성해 호출한다.
  * 조인 등 복잡한 쿼리에서는 동작하지 않는다.
* 내용과 전체 카운트를 조회하는 쿼리를 각각 작성해야 한다.
* `PageableExecutionUtils` 👉 CountQuery 최적화
  * 스프링 데이터 라이브러리가 제공하는 기술이다.
    * 스프링 부트 2.6 부터 패키지가 변경 (`data.repository.support` → `data.support`) 되었다.
  * count 쿼리를 생략 가능한 특수한 상황에 생략한다.
    * `countQuery` 는 생략 가능한 경우를 대비해 `fetch()` 를 실행하지 않고 파라미터로 넘어간다.
  * 내부적으로는 `PageImpl<>(..)` 이 동작한다.

## 스프링 데이터 정렬
```java
JPAQuery<Member> query = queryFactory
            .selectFrom(member);

for (Sort.Order o : pageable.getSort()) {
	PathBuilder pathBuilder = new PathBuilder(member.getType(), member.getMetadata());
    query.orderBy(new OrderSpecifier(o.isAscending() ? Order.ASC : Order.DESC,
    		pathBuilder.get(o.getProperty())));
}

List<Member> result = query.fetch();
```

* 정렬 조건이 조금만 복잡해져도 `Pageable` 의 Sort 기능을 사용하기 어렵다.
* 루트 엔티티 범위를 넘어가거나 동적 정렬 기능은 애플리케이션 로직에서 해결하는 것을 권장한다.

---

# 스프링 데이터 JPA 가 제공하는 Querydsl 기타 기능
## QuerydslPredicateExecutor - 인터페이스 지원
#### QuerydslPredicateExecutor 인터페이스
```java
public interface QuerydslPredicateExecutor<T> {
      Optional<T> findById(Predicate predicate);
      Iterable<T> findAll(Predicate predicate);
      long count(Predicate predicate);
      boolean exists(Predicate predicate);
      
      ...
}
```

#### 리포지토리 적용
```java
public interface MemberRepository extends 
	JpaRepository<User, Long>,
    QuerydslPredicateExecutor<User>
{
	...
}
```
```java
Iterable result = memberRepository.findAll(
				member.age.between(10, 40)
            	.and(member.username.eq("member1")));
```

* `MemberRepositoryImpl` 등 Querydsl 구현체 없이 `Predicate` 를 사용할 수 있다.
* 조인이 불가능 하다. 
  * 묵시적 조인은 가능하지만, leftJoin 이 불가능하다.
* 서비스 계층이 Querydsl 특정 리포지토리 기술에 의존해야 한다.
* 쓰지 말자.

## Querydsl Web
```java
@Controller
class UserController {

    private final UserRepository repository;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    String index(@RequestParam MultiValueMap<String, String> parameters,
    			 @QuerydslPredicate(root = User.class) Predicate predicate,
                 Pageable pageable, 
                 Model model
    ) {
        model.addAttribute("users", repository.findAll(predicate, pageable));
        return "index";
    }
}
```
* 단순한 조건만 가능하고 명시적이지 않다.
* 컨트롤러가 Querydsl 에 의존한다.
* 쓰지 말자.

## QuerydslRepositorySupport - 리포지토리 지원
```java
public class MemberRepositoryImpl 
		extends QuerydslRepositorySupport 
        implements MemberRepositoryCustom
{

    public MemberRepositoryCustomImplQuerySupport(EntityManager em) {
        super(Member.class);
    }
    
    @Override
    public Page<MemberTeamDto> searchPage(
    		MemberSearchCond condition, 
            Pageable pageable
    ) {
        JPQLQuery<MemberTeamDto> jpqlQuery = from(member)
                .leftJoin(member.team, team)
                .where(
                        usernameEq(condition.getUsername()),
                        teamNameEq(condition.getTeamName()),
                        ageGoe(condition.getAgeGoe()),
                        ageLoe(condition.getAgeLoe()))
                .select(new QMemberTeamDto(
                        member.id.as("memberId"),
                        member.username,
                        member.age,
                        team.id.as("teamId"),
                        team.name.as("teamName")));

        JPQLQuery<MemberTeamDto> jpaQuery = getQuerydsl()
        							.applyPagination(pageable, jpqlQuery);
        QueryResults<MemberTeamDto> result = jpaQuery.fetchResults();

        List<MemberTeamDto> content = result.getResults();
        long total = result.getTotal();

        return new PageImpl<>(content, pageable, total);
    }
}
```

* `getQuerydsl().applyPagination()` 을 통해 스프링 데이터가 제공하는 페이징을 `offset()`, `limit()` 없이 Querydsl 에서 사용할 수 있다.
  * 단, Sort 는 오류가 발생한다.
  * 내부적으로는 스프링 데이터가 제공하는 Querydsl 클래스를 통해 EntityManager 가 동작한다.
* 메소드 체인이 끊기면서 가독성이 떨어진다.
* Querydsl 4.x에 나온 JPAQueryFactory 를 제공하지 않는다.
  * 기존처럼 별도로 생성자를 통해 EntityManager 를 받거나 JPQQueryFactory 를 스프링 빈으로 등록해야 한다.
    * 스프링 데이터가 제공하는 EntityManager 가 의미가 없다.
  * Querydsl 3.x 버전을 대상으로 만들어졌다.
* `select` 로 시작할 수 없다. `from` 으로 시작해야 한다.
* 쓰지 말자.

---