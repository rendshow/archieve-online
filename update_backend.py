file_path = 'src/main/java/com/danganguan/archive/file/service/impl/UploadedFileServiceImpl.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    "task.setStatus(TaskStatus.DRAFT);\n        task.setUpdatedAt(LocalDateTime.now());\n        archiveTaskService.updateById(task);",
    "if (task.getStatus() != TaskStatus.PENDING_PROCESS && task.getStatus() != TaskStatus.PROCESSING) {\n            task.setStatus(TaskStatus.DRAFT);\n            task.setUpdatedAt(LocalDateTime.now());\n            archiveTaskService.updateById(task);\n        }"
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print('Done')
