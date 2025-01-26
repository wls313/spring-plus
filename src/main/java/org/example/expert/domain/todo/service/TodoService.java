package org.example.expert.domain.todo.service;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.example.expert.client.WeatherClient;
import org.example.expert.domain.common.dto.AuthUser;
import org.example.expert.domain.common.exception.InvalidRequestException;
import org.example.expert.domain.todo.dto.request.TodoSaveRequest;
import org.example.expert.domain.todo.dto.response.TodoResponse;
import org.example.expert.domain.todo.dto.response.TodoSaveResponse;
import org.example.expert.domain.todo.entity.QTodo;
import org.example.expert.domain.todo.entity.Todo;
import org.example.expert.domain.todo.repository.TodoRepository;
import org.example.expert.domain.user.dto.response.UserResponse;
import org.example.expert.domain.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional //readOnly 가 true 인 경우에는 Get 을 제외한 다른 행동을 할수없다
public class TodoService {

    @Autowired
    EntityManager em;

    private final TodoRepository todoRepository;
    private final WeatherClient weatherClient;

    public TodoSaveResponse saveTodo(AuthUser authUser, TodoSaveRequest todoSaveRequest) {
        User user = User.fromAuthUser(authUser);

        String weather = weatherClient.getTodayWeather();

        Todo newTodo = new Todo(
                todoSaveRequest.getTitle(),
                todoSaveRequest.getContents(),
                weather,
                user
        );
        Todo savedTodo = todoRepository.save(newTodo);

        return new TodoSaveResponse(
                savedTodo.getId(),
                savedTodo.getTitle(),
                savedTodo.getContents(),
                weather,
                new UserResponse(user.getId(), user.getEmail())
        );
    }

    public Page<TodoResponse> getTodos(int page, int size, String weather, LocalDateTime startDate, LocalDateTime endDate) {
        Pageable pageable = PageRequest.of(page - 1, size);

        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
        QTodo todo = QTodo.todo;

        BooleanExpression predicate = todo.isNotNull();

        if (weather != null && !weather.isEmpty()) {
            predicate = predicate.and(todo.weather.eq(weather));
        }
        if (startDate != null) {
            predicate = predicate.and(todo.modifiedAt.goe(startDate));
        }
        if (endDate != null) {
            predicate = predicate.and(todo.modifiedAt.loe(endDate));
        }

        List<Todo> todos = queryFactory
                .selectFrom(todo)
                .where(predicate)
                .orderBy(todo.modifiedAt.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        long total = queryFactory
                .selectFrom(todo)
                .where(predicate)
                .fetchCount();

        List<TodoResponse> todoResponses = todos.stream()
                .map(todoItem -> new TodoResponse(
                        todoItem.getId(),
                        todoItem.getTitle(),
                        todoItem.getContents(),
                        todoItem.getWeather(),
                        new UserResponse(todoItem.getUser().getId(), todoItem.getUser().getEmail()),
                        todoItem.getCreatedAt(),
                        todoItem.getModifiedAt()
                ))
                .toList();

        return new PageImpl<>(todoResponses, pageable, total);
    }

    public TodoResponse getTodo(long todoId) {
        Todo todo = todoRepository.findByIdWithUser(todoId)
                .orElseThrow(() -> new InvalidRequestException("Todo not found"));

        User user = todo.getUser();

        return new TodoResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getContents(),
                todo.getWeather(),
                new UserResponse(user.getId(), user.getEmail()),
                todo.getCreatedAt(),
                todo.getModifiedAt()
        );
    }
}
