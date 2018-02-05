.entry start

#Text section
.section
.base 0x1000

start: 
  outputstr hello

  outputstr adding
  push $2
  push $3
  add
  outputnum
  exit $0
  

#Data section
.section
.base 0x2000

hello:
  .str "Hello world!\n"
adding:
  .str "2 + 3 = "

