<template>
<div class="all">
  <div class="left"></div>
  <div class="right"></div>
  <div class="webrtc_face_recognition">
    <div class="see">
      <div class="bj">
         <img :src="imgSrc" width="100%" height="100%" alt="" />
      </div>
      <div class="video">
        <video
        id="myVideo"
        poster=""
        muted
        loop
        playsinline
        @loadedmetadata="fnRun"
      ></video></div>
      <canvas id="myCanvas" />
      <canvas id="saveFace" />
      <h1 id="showusername" ></h1>
      
      
      
    </div>
    <div class="option">
      <div>
        <label>面板操作：</label>
        <button @click="fnOpen">启动摄像头视频媒体</button>
        <button @click="fnClose">结束摄像头视频媒体</button>
      </div>
    </div>
  </div>

</div>
</template>

<script>
import * as faceapi from "face-api.js";
import axios from 'axios'

export default {
  name: "WebRTCFaceRecognition",
  data() {
    return {
       imgSrc:require('../images/bg.jpg'),
      username: "123",
      nets: "tinyFaceDetector", // 模型
      options: null, // 模型参数
      withBoxes: true, // 框or轮廓
      detectFace: "detectSingleFace", // 单or多人脸
      detection: "landmark",
      videoEl: null,
      canvasEl: null,
      timeout: 0,
      // 视频媒体参数配置
      constraints: {
        audio: false,
        video: {
          // ideal（应用最理想的）
          width: {
            min: 520,
            ideal: 40,
            max: 1920,
          },
          height: {
            min: 440,
            ideal: 40,
            max: 1080,
          },
          // frameRate受限带宽传输时，低帧率可能更适宜
          frameRate: {
            min: 15,
            ideal: 30,
            max: 60,
          },
          // 显示模式前置后置
          facingMode: "environment",
        },
      },
    };
  },
  watch: {
    nets(val) {
      this.nets = val;
      this.fnInit();
    },
    detection(val) {
      this.detection = val;
      this.videoEl.pause();
      setTimeout(() => {
        this.videoEl.play();
        setTimeout(() => this.fnRun(), 1000);
      }, 1000);
    },
  },
  mounted() {
    this.$nextTick(() => {
      this.fnInit();
    });
  },
  methods: {
    // 初始化模型加载
    async fnInit() {
      await faceapi.nets[this.nets].loadFromUri("/models"); // 算法模型
      await faceapi.loadFaceLandmarkModel("/models"); // 轮廓模型
      await faceapi.loadFaceExpressionModel("/models"); // 表情模型
      await faceapi.loadAgeGenderModel("/models"); // 年龄模型
      // 根据算法模型参数识别调整结果
      switch (this.nets) {
        case "ssdMobilenetv1":
          this.options = new faceapi.SsdMobilenetv1Options({
            minConfidence: 0.5, // 0.1 ~ 0.9
          });
          break;
        case "tinyFaceDetector":
          this.options = new faceapi.TinyFaceDetectorOptions({
            inputSize: 512, // 160 224 320 416 512 608
            scoreThreshold: 0.5, // 0.1 ~ 0.9
          });
          break;
        case "mtcnn":
          this.options = new faceapi.MtcnnOptions({
            minFaceSize: 20, // 0.1 ~ 0.9
            scaleFactor: 0.709, // 0.1 ~ 0.9
          });
          break;
      }
      // 节点属性化
      this.videoEl = document.getElementById("myVideo");
      this.canvasEl = document.getElementById("myCanvas");
    },
    // 人脸面部勘探轮廓识别绘制
    async fnRunFaceLandmark() {
      console.log("RunFaceLandmark");
      if (this.videoEl.paused) return clearTimeout(this.timeout);
      //识别绘制人脸信息
      const result = await faceapi[this.detectFace](
        this.videoEl,
        this.options
      ).withFaceLandmarks();
      if (result && !this.videoEl.paused) {
        const dims = faceapi.matchDimensions(this.canvasEl, this.videoEl, true);
        const resizeResult = faceapi.resizeResults(result, dims);
        //       //  在这里准备开始到了人脸的信息
        let ctx = this.canvasEl.getContext("2d");
        // 把当前视频帧内容渲染到canvas上
        ctx.drawImage(this.videoEl, 0, 0);
        // 转base64格式、图片格式转换、图片质量压缩
        var imgBase64 = this.canvasEl.toDataURL("image/jpeg", 1);
        axios.post("http://localhost:8080/admin/facesearch", {
            imagebase64: imgBase64,
          })
          .then(function (response) {
            let username = response.data.data.username
             if (response.data.data.username != null) {
              console.log("人脸识别成功");
              document.getElementById("showusername").innerHTML = "你好"+username+"同学"
            }
          })
          .catch(function (error) {
            console.log(error);
          });
      

        this.withBoxes
          ? faceapi.draw.drawDetections(this.canvasEl, resizeResult)
          : faceapi.draw.drawFaceLandmarks(this.canvasEl, resizeResult);
      } else {
        this.canvasEl
          .getContext("2d")
          .clearRect(0, 0, this.canvasEl.width, this.canvasEl.height);
      }

      this.timeout = setTimeout(() => this.fnRunFaceLandmark(),200);
    },

    // 执行检测识别类型
    fnRun() {
      if (this.detection === "landmark") {
        this.fnRunFaceLandmark();
        return;
      }
      if (this.detection === "expression") {
        this.fnRunFaceExpression();
        return;
      }
      if (this.detection === "age_gender") {
        this.fnRunFaceAgeAndGender();
        return;
      }
    },
    // 启动摄像头视频媒体
    fnOpen() {
      if (typeof window.stream === "object") return;
      clearTimeout(this.timeout);
      this.timeout = setTimeout(() => {
        clearTimeout(this.timeout);
        navigator.mediaDevices
          .getUserMedia(this.constraints)
          .then(this.fnSuccess)
          .catch(this.fnError);
      }, 300);
    },
    // 成功启动视频媒体流
    fnSuccess(stream) {
      window.stream = stream; // 使流对浏览器控制台可用
      this.videoEl.srcObject = stream;
      this.videoEl.play();
    },
    // 失败启动视频媒体流
    fnError(error) {
      console.log(error);
      alert("视频媒体流获取错误" + error);
    },
    // 结束摄像头视频媒体
    fnClose() {
      this.canvasEl
        .getContext("2d")
        .clearRect(0, 0, this.canvasEl.width, this.canvasEl.height);
      this.videoEl.pause();
      clearTimeout(this.timeout);
      if (typeof window.stream === "object") {
        window.stream.getTracks().forEach((track) => track.stop());
        window.stream = "";
        this.videoEl.srcObject = null;
      }
    },
  },
  beforeDestroy() {
    this.fnClose();
  },
};
</script>

<style scoped>
.all {
  position: fixed;
  width: 100%;
}
.left {
  width: 400px;
  height: 650px;
  float: left;
  background: rgb(187, 186, 186);
}
.right {
  width: 400px;
  height: 650px;
  float: right;
  background: rgb(187, 186, 186);
}
.webrtc_face_recognition {
  
  width: 560px;

  position: relative;
  top: 0px;
  left: 0px;
  border: 2px #42b983 solid;
  margin: 0 auto;
}
.video {
  width: 520px;
  height: 440px;
  
  margin-top: 20px;
  z-index: 1;
  
}
.bj {
  width: 520px;
  height: 440px;
  
  
  float: left;
  z-index: -1;
  position: absolute;
}



button {
  height: 30px;
  border: 2px #42b983 solid;
  border-radius: 4px;
  background: #42b983;
  color: white;
  margin: 10px;
  cursor: pointer;
}
.see {
  width: 520px;
  height: 520px;
 position: relative;
 margin: auto;
 top: 10%;
}
.see canvas {
  position: absolute;
  top: 0;
  left: 0;
}
.see h1 {
padding-left: 140px;

}
.option {
  width: 520px;
  padding-bottom: 20px;
  position: relative;
  margin: auto;
}
.option div {
  padding: 10px;

}
.option div label {
  margin-right: 20px;
}
</style>
