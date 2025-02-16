/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.vision.demo;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 既存のExecutorをラップし、送信されたRunnableを後でキャンセルできる{@link #shutdown}メソッドを提供します。
 */
public class ScopedExecutor implements Executor {

    private final Executor executor;
    private final AtomicBoolean shutdown = new AtomicBoolean();

    /**
     * ScopedExecutorのコンストラクタです。
     *
     * @param executor ラップする既存のExecutor
     */
    public ScopedExecutor(@NonNull Executor executor) {
        this.executor = executor;
    }

    /**
     * Runnableを実行します。ただし、ScopedExecutorがシャットダウンされている場合は実行しません。
     *
     * @param command 実行するRunnable
     */
    @Override
    public void execute(@NonNull Runnable command) {
        // このオブジェクトがシャットダウンされている場合、早期にリターンします。
        if (shutdown.get()) {
            return;
        }
        executor.execute(
                () -> {
                    // 同時にシャットダウンされた場合に備えて再度チェックします。
                    if (shutdown.get()) {
                        return;
                    }
                    command.run();
                });
    }

    /**
     * このメソッドを呼び出すと、送信されたり後で送信されるRunnableは実行されず、
     * ScopedExecutorは何も行わない状態になります。
     * <p>
     * 既に実行が開始されているRunnableは継続します。
     */
    public void shutdown() {
        shutdown.set(true);
    }
}
