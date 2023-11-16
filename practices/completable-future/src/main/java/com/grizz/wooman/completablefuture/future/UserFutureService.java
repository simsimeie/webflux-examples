package com.grizz.wooman.completablefuture.future;

import com.grizz.wooman.completablefuture.common.Article;
import com.grizz.wooman.completablefuture.common.Image;
import com.grizz.wooman.completablefuture.common.User;
import com.grizz.wooman.completablefuture.common.repository.UserEntity;
import com.grizz.wooman.completablefuture.future.repository.ArticleFutureRepository;
import com.grizz.wooman.completablefuture.future.repository.FollowFutureRepository;
import com.grizz.wooman.completablefuture.future.repository.ImageFutureRepository;
import com.grizz.wooman.completablefuture.future.repository.UserFutureRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class UserFutureService {
    private final UserFutureRepository userRepository;
    private final ArticleFutureRepository articleRepository;
    private final ImageFutureRepository imageRepository;
    private final FollowFutureRepository followRepository;

    @SneakyThrows
    public CompletableFuture<Optional<User>> getUserById(String id) {
        return userRepository.findById(id)
                .thenComposeAsync(this::getUser);
    }

    @SneakyThrows
    private CompletableFuture<Optional<User>> getUser(Optional<UserEntity> userEntityOptional) {
        if (userEntityOptional.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        var userEntity = userEntityOptional.get();

        var imageFuture = imageRepository.findById(userEntity.getProfileImageId())
                .thenApplyAsync(imageEntityOptional ->
                        imageEntityOptional.map(imageEntity ->
                            new Image(imageEntity.getId(), imageEntity.getName(), imageEntity.getUrl())
                        )
                );


        var articlesFuture = articleRepository.findAllByUserId(userEntity.getId())
                .thenApplyAsync(articleEntities ->
                        articleEntities.stream()
                                .map(articleEntity ->
                                    new Article(articleEntity.getId(), articleEntity.getTitle(), articleEntity.getContent())
                                )
                        .collect(Collectors.toList())
                );

        var followCountFuture = followRepository.countByUserId(userEntity.getId());

        return CompletableFuture.allOf(imageFuture, articlesFuture, followCountFuture)
                .thenAcceptAsync(v -> {
                    log.info("Three futures are completed");
                })
                .thenRunAsync(() -> {
                    log.info("Three futures are also completed");
                })
                // 어차피 allOf 메서드는 Void를 반환하기 때문에 Upstream에서 thenAcceptAsync, thenRunAsync 사용하여 null이 downstream으로 떨어지더라도 상관이 없다.
                // 실제 Downstream에서 사용할 값은 각 Future의 get 메서드를 사용해 가져오기 때문
                .thenApplyAsync(v -> {
                    try {
                        var image = imageFuture.get();
                        var articles = articlesFuture.get();
                        var followCount = followCountFuture.get();

                        return Optional.of(
                                new User(
                                        userEntity.getId(),
                                        userEntity.getName(),
                                        userEntity.getAge(),
                                        image,
                                        articles,
                                        followCount
                                )
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
