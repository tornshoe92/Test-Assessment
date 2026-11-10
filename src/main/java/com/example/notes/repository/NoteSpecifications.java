package com.example.notes.repository;

import com.example.notes.domain.Note;
import com.example.notes.domain.NoteShare;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Reusable predicates for note queries.
 *
 * <p>Access is expressed with correlated {@code EXISTS} subqueries rather than
 * joins, so a note shared several ways still counts once and paging stays
 * accurate without {@code DISTINCT}.
 */
public final class NoteSpecifications {

    private NoteSpecifications() {
    }

    /** Notes the user owns, or that are shared with them directly or via one of their teams. */
    public static Specification<Note> accessibleBy(Long userId, Collection<Long> teamIds) {
        return (root, query, cb) -> {
            List<Predicate> options = new ArrayList<>();
            options.add(cb.equal(root.get("owner").get("id"), userId));

            Subquery<Long> directShare = query.subquery(Long.class);
            Root<NoteShare> ds = directShare.from(NoteShare.class);
            directShare.select(ds.get("id")).where(
                    cb.equal(ds.get("note").get("id"), root.get("id")),
                    cb.equal(ds.get("sharedWithUser").get("id"), userId));
            options.add(cb.exists(directShare));

            if (teamIds != null && !teamIds.isEmpty()) {
                Subquery<Long> teamShare = query.subquery(Long.class);
                Root<NoteShare> ts = teamShare.from(NoteShare.class);
                teamShare.select(ts.get("id")).where(
                        cb.equal(ts.get("note").get("id"), root.get("id")),
                        ts.get("sharedWithTeam").get("id").in(teamIds));
                options.add(cb.exists(teamShare));
            }

            return cb.or(options.toArray(new Predicate[0]));
        };
    }

    public static Specification<Note> ownedBy(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("owner").get("id"), userId);
    }

    public static Specification<Note> notOwnedBy(Long userId) {
        return (root, query, cb) -> cb.notEqual(root.get("owner").get("id"), userId);
    }

    /** Case-insensitive substring match against title or content. */
    public static Specification<Note> matchesText(String text) {
        String pattern = "%" + text.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("content")), pattern));
    }

    /** Tags are normalised to lower case on write, so an exact match is enough. */
    public static Specification<Note> hasTag(String tag) {
        String normalised = tag.toLowerCase();
        return (root, query, cb) -> {
            Join<Note, String> tags = root.join("tags");
            return cb.equal(tags, normalised);
        };
    }
}
