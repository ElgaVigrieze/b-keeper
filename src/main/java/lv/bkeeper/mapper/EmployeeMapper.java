package lv.bkeeper.mapper;

import lv.bkeeper.domain.Employee;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Copies the employee form fields onto the persisted row when editing.
 *
 * <p>{@code nullValuePropertyMappingStrategy = IGNORE}: a field the form did not post
 * (source {@code null}) leaves the persisted value untouched instead of overwriting it
 * with {@code null}. {@code createdAt}/{@code updatedAt} are never posted and are owned
 * by the controller, so they are simply not copied here.
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface EmployeeMapper {

    /**
     * Copies editable fields from {@code submitted} onto the persisted {@code existing}.
     * The caller still owns {@code updatedAt} and the repository save.
     */
    void update(@MappingTarget Employee existing, Employee submitted);
}
