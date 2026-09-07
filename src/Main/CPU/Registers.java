package Main.CPU;
public class Registers {

    // registers R0 - R7
    private final int[] r = new int[8];

    // Special registers
    private int accumulator; 
    private int b;           
    private int pc;         
    private int sp;         
    private int psw;       

    public int getR(int index) {
        checkRegisterIndex(index);
        return r[index];
    }

    public void setR(int index, int value) {
        checkRegisterIndex(index);
        r[index] = value & 0xFF;
    }

    private void checkRegisterIndex(int index) {
        if (index < 0 || index > 7) {
            throw new IllegalArgumentException(
                "Register must be between R0 and R7"
            );
        }
    }

    //accumulator A

    public int getAccumulator() {
        return accumulator;
    }

    public void setAccumulator(int value) {
        accumulator = value & 0xFF;
    }

    //B register

    public int getB() {
        return b;
    }

    public void setB(int value) {
        b = value & 0xFF;
    }

    //Program Counter

    public int getPC() {
        return pc;
    }

    public void setPC(int value) {
        pc = value;
    }

    //Stack Ponter

    public int getSP() {
        return sp;
    }

    public void setSP(int value) {
        sp = value & 0xFF;
    }

    //Program Status Word

    public int getPSW() {
        return psw;
    }

    public void setPSW(int value) {
        psw = value & 0xFF;
    }
    
    public boolean isCarry() {
    return (psw & 0x80) != 0;
}

public void setCarry(boolean value) {
    if (value) {
        psw |= 0x80;
    } else {
        psw &= ~0x80;
    }
}
// Auxiliary Carry (AC) - PSW bit 6
public boolean isAuxiliaryCarry() {
    return (psw & 0x40) != 0;
}

public void setAuxiliaryCarry(boolean value) {
    if (value) {
        psw |= 0x40;
    } else {
        psw &= ~0x40;
    }
}

// Overflow (OV) - PSW bit 2
public boolean isOverflow() {
    return (psw & 0x04) != 0;
}

public void setOverflow(boolean value) {
    if (value) {
        psw |= 0x04;
    } else {
        psw &= ~0x04;
    }
}

    // ---------- Reset ----------

    public void reset() {

        for (int i = 0; i < r.length; i++) {
            r[i] = 0;
        }

        accumulator = 0;
        b = 0;
        pc = 0;
        sp = 0;
        psw = 0;
    }
}
