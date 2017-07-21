# RE^2 Assembler Language

RE^2 is a 16-bit based architecture I designed just for fun. This was designed as a 
hybrid between java bytecode and x86 architecture. This repository contains an 
assembler and an interpreter.

## Basic Mechanics
RE^2 mechanics works with the premise of a "value stack". All basic arithmetic 
operations will carry out a combination of pops and pushes to execute the particular
operation and to "return" the value. It first pops the operands directly from this 
stack, and then pushes the result to the stack. 

This stack starts from `0xfff0` and will grow in the decreasing address direction.
This is also used in preserving call stacks. The fact that both intermediary values
and call preservation values are both on the stack makes this an intentionally complex
architecture. 

### Registers
Registers are also utilized within this specification (as if a value stack isn't 
enough!) as temorary variables. There are 16 16-bit registers (maybe a little too 
many of them!), with the last three registers being special: `reg15` is the `IP` 
(instruction pointer), `reg14` is the `BP` (base (frame) pointer), `reg13` is the `SP`
(stack pointer). 
