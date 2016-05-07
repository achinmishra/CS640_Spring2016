JCC = javac
JFLAGS = -g
EXECUTABLE = edu.wisc.cs.sdn.simpledns.SimpleDNS.class

default: $(EXECUTABLE)

$(EXECUTABLE): edu/wisc/cs/sdn/simpledns/SimpleDNS.java
	$(JCC) $(JFLAGS) -cp . $^

.PHONY: clean

clean:
	rm -f -v $(EXECUTABLE)

