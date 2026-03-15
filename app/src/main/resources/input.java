// Single-line comment - should be skipped entirely
int x = 10;
float y = 3.14f;

/* Multi-line comment
   spanning several lines
   should also be skipped */
int result = x + 2;

if (x > 0) {
    y = y + 1.5;
}

while (x != 0) {
    x = x - 1;
}

// Testing identifiers and keywords
boolean flag = true;
String name = null;

// Error recovery: $ and ` are not valid tokens - scanner should report them
int z = x $$$$ y;
int w = x ` 5;

// Operators and punctuation mix
result = (x * y) / 2 + result - 1;
flag = (x >= 10) && (y <= 5.0f);
