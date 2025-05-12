

1. 준비: PC에서 mjpeg_audio_server2.py를 RUN한다.  import 오류가 발생하면 라이브러리를 추가한다.
 - pip install flask
 - pip install opencv-python
 - pip install pyaudio
 - 정상적으로 실행되면 서버의 ip가 표시된다.
 - domain을 갖고 있다면 domainm 주소로 잘 연결되는지 테스트 한다.
 - 웹브라우저에서 http://xxx.xxx.xx.xx/video 또는 http://해당도메인/video 로 연결해본다.  
 
2. 서버가 준비되었으면 앱을 실행한다.
3. 화면의 ip주소 입력칸에 서버의 ip(또는 도메인 주소)를 입력하고 "Connect"버튼을 클릭한다.
4. 연결이 잘되면 서버에 연결된 카메라 정보가 앱 화면에 나옵니다.
5. 음성을 전송할때는 하단의 "Hold to Record" 버튼을 클릭하고 음성을 말하면 서버로 전송됩니다. 버튼에서 손을떼면 전송중지
   
