package pipiolo.querydsl.repository;

import com.querydsl.core.QueryResults;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;
import pipiolo.querydsl.dto.MemberSearchCond;
import pipiolo.querydsl.dto.MemberTeamDto;
import pipiolo.querydsl.dto.QMemberTeamDto;
import pipiolo.querydsl.entity.Member;

import javax.persistence.EntityManager;
import java.util.List;

import static org.springframework.util.StringUtils.hasText;
import static pipiolo.querydsl.entity.QMember.member;
import static pipiolo.querydsl.entity.QTeam.team;

public class MemberRepositoryCustomImplQuerySupport extends QuerydslRepositorySupport implements MemberRepositoryCustom {

    private final JPAQueryFactory query;

    public MemberRepositoryCustomImplQuerySupport(EntityManager em) {
        super(Member.class);
        query = new JPAQueryFactory(em);
    }

    // Querydsl 3.x 버전에서는 from 절부터 시작했어야 했음.
    // JPAQueryFactory 기능이 없었전 시절
    // Querydsl 4.x 에 나온 JPAQueryFactory 을 사용 못함
    // Sprign Data Sort 기능이 정상 동작하지 않음.
    // JPAQueryFactory 을 사용하기 위해서는 별도로 받으면 된다.
    @Override
    public List<MemberTeamDto> search(MemberSearchCond condition) {
        return from(member)
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
                        team.name.as("teamName")))
                .fetch();
    }

    // 중간에 코드가 끊긴다.
    // 메소드 체인으로 쭉쭉 가다가 체인이 끊기면서 읽기가 어렵다.
    // 거의 유일한 장점은 페이징할때, page(), offset() 코드 2줄을 아낄수 있다.
    // 하지만 체인이 끊겨 결국 2줄을 추가해야 되고, 가독성도 떨어진다.
    // 쓰지 말자. -> 직접 Querydsl 지원 클래스를 만들자.
    @Override
    public Page<MemberTeamDto> searchPageSimple(MemberSearchCond condition, Pageable pageable) {
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

        JPQLQuery<MemberTeamDto> jpaQuery = getQuerydsl().applyPagination(pageable, jpqlQuery);
        QueryResults<MemberTeamDto> result = jpaQuery.fetchResults();

        List<MemberTeamDto> content = result.getResults();
        long total = result.getTotal();

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<MemberTeamDto> searchPageComplex(MemberSearchCond condition, Pageable pageable) {
        return null;
    }

    @Override
    public Page<MemberTeamDto> searchPageCount(MemberSearchCond condition, Pageable pageable) {
        return null;
    }

    @Override
    public Page<MemberTeamDto> searchPageFinal(MemberSearchCond condition, Pageable pageable) {
        return null;
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
