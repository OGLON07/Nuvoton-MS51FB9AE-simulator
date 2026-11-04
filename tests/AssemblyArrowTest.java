import Main.instruction.AssemblyAssembler;
import Main.instruction.DemoPrograms;
import java.util.Map;

public class AssemblyArrowTest {
    public static void main(String[] args) {
        testDemo("example", DemoPrograms.example());
        testDemo("stack", DemoPrograms.stack());
        testDemo("queue", DemoPrograms.queue());
        testDemo("hamming", DemoPrograms.hamming());
        System.out.println("ALL ASSEMBLY ARROW TESTS PASSED!");
    }

    private static void testDemo(String name, String source) {
        AssemblyAssembler.AssemblyResult result = AssemblyAssembler.assembleWithSourceMap(source);
        assert result.machineCode != null && result.machineCode.length > 0 : name + " machineCode empty";
        assert result.pcToLine != null && !result.pcToLine.isEmpty() : name + " pcToLine empty";
        assert result.pcToLine.containsKey(0) : name + " must have mapping for PC=0";

        String[] lines = source.replace("\r", "").split("\n");
        int firstLine = result.pcToLine.get(0);
        assert firstLine >= 0 && firstLine < lines.length : name + " PC=0 line out of bounds";
        assert !lines[firstLine].trim().startsWith(";") : name + " PC=0 should not point to comment";

        for (Map.Entry<Integer, Integer> entry : result.pcToLine.entrySet()) {
            int pc = entry.getKey();
            int line = entry.getValue();
            assert pc >= 0 && pc < result.machineCode.length : name + " pc " + pc + " out of machineCode bounds";
            assert line >= 0 && line < lines.length : name + " line " + line + " out of bounds";
        }
        System.out.printf("  [PASS] %-10s: %3d bytes, %2d instructions mapped%n", name, result.machineCode.length, result.pcToLine.size());
    }
}

