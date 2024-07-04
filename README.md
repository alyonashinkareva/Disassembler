# Disassembler
A translator program (disassembler) that converts machine code into program text in assembly language.
How to use:
1. Open source ELF file for reading
2. Read ELF file in binary form, parse Header, read
   header sections, find the necessary sections (.text, .symtab) and parse them: parse .text: you need to go through all the commands, remember the labels and their position, parse the commands, parse .symtab.
3. Output two sections to the output file: .text, which contains assembler commands, .symtab, which is a table of symbols.
4. In addition, the program handles errors that
   may arise during execution:
   - incorrect input file format
   - failed to open file
   - failed to write to file
   - failed to close the file
   - a value was passed that is not supported
* Основной парсер - Class Parser с дополнительными вспомогательными классами.
* The program is launched in the Main class file. There the following arguments are given as input: args[0] - rv3 (name of the executable file) in the case of Java this argument has no meaning, args[1] - name of the input ELF file, args[2] - name of the output file