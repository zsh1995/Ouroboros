# 尾递归优化
## 尾递归
> 如果一个函数中所有递归形式的调用都出现在函数的末尾，我们称这个递归函数是尾递归的。当递归调用是整个函数体中最后执行的语句且它的返回值不属于表达式的一部分时，这个递归调用就是尾递归。尾递归函数的特点是在回归过程中不用做任何操作。
## 原理
尾递归方法在返回时，要么返回一个常量，要么返回对自身方法调用的结果。从字节码的角度看，一定存在某个返回指令（ `IRETURN`，`LRETURN`），在它前面有个对自身的 `invoke`。在 JVM 中，方法的入参是存在局部变量表 local 中的，而需要返回的值及调用方法的参数是存在操作数栈上的。为了减少栈帧的产生，我们需要把对自身的调用及返回去掉，而换为跳转到方法的开始，为了使得局部变量表及操作数栈变为和调用方法一致，需要先把栈上值放到局部变量表中。

## 使用
1. 编译 jar 包
> `mvn package `
2. 进入`examples`目录，编译带`@TailRecursion`的源文件`Fibo.java`，指定 classpath
> `javac -cp opt-tail-recursion-1.0-SNAPSHOT.jar Fibo.java`
3. 使用编译的 agent 运行代码,
> `java -javaagent:.\opt-tail-recursion-1.0-SNAPSHOT.jar Fibo [:<第 n 项>...]`

## agent 命令
使用debug模式，默认输出debug.log到当前目录下，修改后的 class 文件到 ./output 下。
> `java -javaagent:.\opt-tail-recursion-1.0-SNAPSHOT.jar='-d [y|n] -l <log path> -o <修改后类文件保存目录>' [:<类名>]`