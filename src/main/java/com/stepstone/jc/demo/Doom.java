package com.stepstone.jc.demo;

import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ExternalGlobal;
import com.dylibso.chicory.runtime.ExternalValues;
import com.dylibso.chicory.runtime.ExternalMemory;
import com.dylibso.chicory.runtime.ExternalTable;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.runtime.WasmFunctionHandle;
import com.dylibso.chicory.wasm.Module;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.wasm.types.MemoryLimits;
import com.dylibso.chicory.wasm.types.Value;
import com.dylibso.chicory.wasm.types.ValueType;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.awt.image.BufferedImage.TYPE_4BYTE_ABGR;

public class Doom {
    static int doomScreenWidth = 640;
    static int doomScreenHeight = 400;
    static String JS_MODULE_NAME = "js";
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
    private final GameWindow gameWindow = new GameWindow();

    public static void main(String[] args) throws Exception {
       new Doom().runGame();
    }

    void runGame() throws IOException {
        EventQueue.invokeLater(() -> gameWindow.setVisible(true));

        byte[] bytes = this.getClass().getResourceAsStream("/doom.wasm").readAllBytes();
        // load WASM module
        var module = Instance.builder(Parser.parse(bytes));

        //        import function js_js_milliseconds_since_start():int;
        //        import function js_js_console_log(a:int, b:int);
        //        import function js_js_draw_screen(a:int);
        //        import function js_js_stdout(a:int, b:int);
        //        import function js_js_stderr(a:int, b:int);
        var imports = new ExternalValues(
                new HostFunction[]{
                        new HostFunction(
                                JS_MODULE_NAME,
                                "js_milliseconds_since_start",
                                jsMillisecondsSinceStart(),
                                List.of(),
                                List.of(ValueType.I32)),
                        new HostFunction(
                                JS_MODULE_NAME,
                                "js_console_log",
                                jsConsoleLog(),
                                List.of(ValueType.I32, ValueType.I32),
                                List.of()),
                        new HostFunction(
                                JS_MODULE_NAME,
                                "js_stdout",
                                jsStdout(),
                                List.of(ValueType.I32, ValueType.I32),
                                List.of()),
                        new HostFunction(
                                JS_MODULE_NAME,
                                "js_stderr",
                                jsStderr(),
                                List.of(ValueType.I32, ValueType.I32),
                                List.of()),
                        new HostFunction(
                                JS_MODULE_NAME,
                                "js_draw_screen",
                                jsDrawScreen(),
                                List.of(ValueType.I32),
                                List.of()),
                },
                new ExternalGlobal[]{},
                new ExternalMemory[]{
                        new ExternalMemory("env", "memory", new Memory(new MemoryLimits(108, 1000)))
                },
                new ExternalTable[]{}
        );
        var instance = module.withExternalValues(imports).build();

        var addBrowserEvent = instance.export("add_browser_event");
        var doomLoopStep = instance.export("doom_loop_step");
        var main = instance.export("main");

        // run main() with doommy argc,argv pointers to set up some variables
        main.apply(Value.i32(0), Value.i32(0));

        // schedule main game loop
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            gameWindow.drainKeyEvents(event -> addBrowserEvent.apply(Value.i32(event[0]), Value.i32(event[1])));
            doomLoopStep.apply();
        }, 0, 10, TimeUnit.MILLISECONDS);
    }

    private final long start = System.currentTimeMillis();

    /**
     * Used by game to track flow of time, to trigger events
     *
     * @return milliseconds from start of game
     */
    private WasmFunctionHandle jsMillisecondsSinceStart() {
        return (Instance instance, Value ... args) -> {
            return new Value[] { Value.i32((int) (System.currentTimeMillis() - start)) };
        };
    }
    private WasmFunctionHandle jsConsoleLog() {
        return (Instance instance, Value ... args) -> {
            var offset = args[0].asInt();
            var size = args[1].asInt();

            System.out.println(instance.memory().readString(offset, size));

            return null;
        };
    }
    private WasmFunctionHandle jsStdout() {
        return (Instance instance, Value ... args) -> {
            var offset = args[0].asInt();
            var size = args[1].asInt();

            System.out.print(instance.memory().readString(offset, size));

            return null;
        };
    }
    private WasmFunctionHandle jsStderr() {
        return (Instance instance, Value ... args) -> {
            var offset = args[0].asInt();
            var size = args[1].asInt();

            System.err.print(instance.memory().readString(offset, size));

            return null;
        };
    }

    /**
     * Called when game draws to screen.
     * Fortunately doom screen buffer can be copied directly into {@link BufferedImage} buffer
     *
     */
    private WasmFunctionHandle jsDrawScreen() {
        return (Instance instance, Value ... args) -> {
            var ptr = args[0].asInt();

            int max = Doom.doomScreenWidth * Doom.doomScreenHeight * 4;
            int[] screenData = new int[max];
            for (int i = 0; i < max; i++) {
                byte pixelComponent = instance.memory().read(i + ptr);
                screenData[i] = pixelComponent;
            }
            BufferedImage bufferedImage = new BufferedImage(Doom.doomScreenWidth, Doom.doomScreenHeight, TYPE_4BYTE_ABGR);
            bufferedImage.getRaster().setPixels(0, 0, Doom.doomScreenWidth, Doom.doomScreenHeight, screenData);
            gameWindow.drawImage(bufferedImage);

            return null;
        };
    }

}
